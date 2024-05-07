(ns datascript.serialize
  (:refer-clojure :exclude [amap array?])
  (:require
    [#?(:cljd cljd.reader :default clojure.edn) :as edn]
    [clojure.string :as str]
    [datascript.db :as db #?(:cljd :refer :cljs :refer-macros :clj :refer) [raise cond+] #?@(:cljs [:refer [Datom]] :cljd [:refer [Datom]])]
    [datascript.lru :as lru]
    [datascript.storage :as storage]
    #?(:cljd nil :default [me.tonsky.persistent-sorted-set :as set])
    #?(:cljd nil :default [me.tonsky.persistent-sorted-set.arrays :as arrays]))
  #?(:cljs (:require-macros [datascript.serialize :refer [array dict]]))
  #?(:cljd nil
     :clj
     (:import
       [datascript.db Datom]
       [me.tonsky.persistent_sorted_set PersistentSortedSet])))

(def ^:private marker-kw 0)
(def ^:private marker-other 1)
(def ^:private marker-inf 2)
(def ^:private marker-minus-inf 3)
(def ^:private marker-nan 4)

(defn- if-cljs [env then else]
  (if (:ns env) then else))

#?(:cljd
   ; cgrand: I wonder if a plain vec wouldn't work as well and that it
   ; doesn't really matter if it's an "array"
   (defmacro array
     "Platform-native array representation (java.util.List on JVM, Array on JS)"
     [& args]
     `(doto (.filled #/(List dynamic) ~(count args) nil)
        ~@(map-indexed
            (fn [i arg]
              `(. "[]=" ~i ~arg))
            args)))
   :clj
   (defmacro array
     "Platform-native array representation (java.util.List on JVM, Array on JS)"
     [& args]
     (if-cljs &env
       (list* 'js* (str "[" (str/join "," (repeat (count args) "~{}")) "]") args)
       (vec args))))

#?(:cljd
   (defmacro dict
     "Platform-native dictionary representation (java.util.Map on JVM, Object on JS)"
     [& args]
     `(hash-map ~@args))
   :clj
   (defmacro dict
     "Platform-native dictionary representation (java.util.Map on JVM, Object on JS)"
     [& args]
     (if-cljs &env
       (list* 'js* (str "{" (str/join "," (repeat (/ (count args) 2) "~{}:~{}")) "}") args)
       `(array-map ~@args))))

(defn- array-get [d i]
  #?(:cljd (. ^List d "[]" (int i))
     :clj  (.get ^java.util.List d (int i))
     :cljs (if (cljs.core/array? d) (arrays/aget d i) (nth d i))))

(defn- dict-get [d k]
  #?(:cljd (. ^Map d "[]" k)
     :clj  (.get ^java.util.Map d k)
     :cljs (if (map? d) (d k) (arrays/aget d k))))

(defn- array? [a]
  #?(:cljd (dart/is? a List)
     :clj  (instance? java.util.List a)
     :cljs (or (cljs.core/array? a) (vector? a))))

(defn- amap [f xs]
  #?(:cljd
     (let [arr (.filled #/(List dynamic) (count xs) nil)]
       (reduce (fn [idx x] (aset arr idx (f x)) (inc idx)) 0 xs)
       arr)
     :clj
     (let [arr (java.util.ArrayList. (count xs))]
       (reduce (fn [idx x] (.add arr (f x)) (inc idx)) 0 xs)
       arr)
     :cljs
     (let [arr (js/Array. (count xs))]
       (reduce (fn [idx x] (arrays/aset arr idx (f x)) (inc idx)) 0 xs)
       arr)))

(defn- amap-indexed [f xs]
  #?(:cljd
     (let [arr (.filled #/(List dynamic) (count xs) nil)]
       (reduce (fn [idx x] (aset arr idx (f idx x)) (inc idx)) 0 xs)
       arr)
     :clj
     (let [arr (java.util.ArrayList. (count xs))]
       (reduce (fn [idx x] (.add arr (f idx x)) (inc idx)) 0 xs)
       arr)
     :cljs
     (let [arr (js/Array. (count xs))]
       (reduce (fn [idx x] (arrays/aset arr idx (f idx x)) (inc idx)) 0 xs)
       arr)))

(defn- attr-comparator
  "Looks for a datom with attribute exactly bigger than the given one"
  [^Datom d1 ^Datom d2]
  (cond
    (nil? (.-a d2)) -1
    (<= (compare (.-a d1) (.-a d2)) 0) -1
    true 1))

(defn- all-attrs
  "All attrs in a DB, distinct, sorted"
  [db]
  (if (empty? (:aevt db))
    []
    (loop [attrs (transient [(:a (first (:aevt db)))])]
      (let [attr      (nth attrs (dec (count attrs)))
            left      (db/min-datom (db/datom db/emax attr nil))
            right     (db/max-datom (db/datom db/emax nil nil))
            next-attr (:a (first #?(:cljd (subseq (:aevt db) > left <= right)
                                    :default (set/slice (:aevt db) left right attr-comparator))))]
        (if (some? next-attr)
          (recur (conj! attrs next-attr))
          (persistent! attrs))))))

(def ^{:arglists '([kw])} freeze-kw str)

(defn- thaw-kw [s]
  (if (str/starts-with? s ":")
    (keyword (subs s 1))
    s))

(defn- serializable-impl
  "Serialized structure breakdown:

   count    :: number
   tx0      :: number
   max-eid  :: number
   max-tx   :: number
   schema   :: freezed :schema
   attrs    :: [keywords ...]
   keywords :: [keywords ...]
   eavt     :: [[e a-idx v dtx] ...]
   a-idx    :: index in attrs
   v        :: (string | number | boolean | [0 <index in keywords>] | [1 <freezed v>])
   dtx      :: tx - tx0
   aevt     :: [<index in eavt> ...]
   avet     :: [<index in eavt> ...]"
  [db {:keys [freeze-fn freeze-kw]
       :or   {freeze-fn pr-str
              freeze-kw freeze-kw}}]
  (when (storage/storage db)
    (throw (ex-info "serializable doesn't work with databases that have :storage" {})))
  (let [attrs       (all-attrs db)
        attrs-map   (into {} (map vector attrs (range)))
        *kws        (volatile! (transient []))
        *kw-map     (volatile! (transient {}))
        write-kw    (fn [kw]
                      (let [idx (or
                                  (get @*kw-map kw)
                                  (let [keywords (vswap! *kws conj! kw)
                                        idx      (dec (count keywords))]
                                    (vswap! *kw-map assoc! kw idx)
                                    idx))]
                        (array marker-kw idx)))
        write-other (fn [v] (array marker-other (freeze-fn v)))
        write-v     (fn [v]
                      (cond
                        (string? v)  v
                        #?@(:cljd [] :clj [(ratio? v) (write-other v)])

                        (number? v)
                        (cond
                          (== ##Inf v)  (array marker-inf)
                          (== ##-Inf v) (array marker-minus-inf)
                          #?(:cljd (.-isNaN ^num v) :clj (Double/isNaN v) :cljs (js/isNaN v)) (array marker-nan)
                          :else v)

                        (boolean? v) v
                        (keyword? v) (write-kw v)
                        true         (write-other v)))
        eavt        (amap-indexed
                      (fn [idx ^Datom d]
                        (db/datom-set-idx d idx)
                        (let [e  (.-e d)
                              a  (attrs-map (.-a d))
                              v  (write-v (.-v d))
                              tx (- (.-tx d) db/tx0)]
                          (array e a v tx)))
                      (:eavt db))
        aevt        (amap-indexed (fn [_ ^Datom d] (db/datom-get-idx d)) (:aevt db))
        avet        (amap-indexed (fn [_ ^Datom d] (db/datom-get-idx d)) (:avet db))
        schema      (freeze-fn (:schema db))
        attrs       (amap freeze-kw attrs)
        kws         (amap freeze-kw (persistent! @*kws))
        #?@(:cljd []
            :clj
            [settings (set/settings (:eavt db))])]
    (dict
      "count"    (count (:eavt db))
      "tx0"      db/tx0
      "max-eid"  (:max-eid db)
      "max-tx"   (:max-tx db)
      "schema"   schema
      "attrs"    attrs
      "keywords" kws
      "eavt"     eavt
      "aevt"     aevt
      "avet"     avet
      #?@(:cljd []
          :clj
          ["branching-factor" (:branching-factor settings)
           "ref-type"         (name (:ref-type settings))]))))

#?(:cljd
   (defn serializable
     ([db] (serializable-impl db {}))
     ([db opts] (serializable-impl db opts)))
   :clj
   (let [lock (Object.)]
     (defn serializable
       ([db] (locking lock (serializable-impl db {})))
       ([db opts] (locking lock (serializable-impl db opts)))))
   :cljs
   (defn serializable
     ([db] (serializable-impl db {}))
     ([db opts] (serializable-impl db opts))))

(defn from-serializable
  ([from]
   (from-serializable from {}))
  ([from {:keys [thaw-fn thaw-kw]
          :or   {thaw-fn edn/read-string
                 thaw-kw thaw-kw}
          :as opts}]
   (let [tx0      (dict-get from "tx0")
         schema   (thaw-fn (dict-get from "schema"))
         _        (#'db/validate-schema schema)
         attrs    (->> (dict-get from "attrs") (mapv thaw-kw))
         keywords (->> (dict-get from "keywords") (mapv thaw-kw))
         eavt     (->> (dict-get from "eavt")
                    (amap (fn [arr]
                            (let [e  (array-get arr 0)
                                  a  (nth attrs (array-get arr 1))
                                  v  (array-get arr 2)
                                  v  (cond
                                       (number? v)  v
                                       (string? v)  v
                                       (boolean? v) v
                                       (array? v) (let [marker (array-get v 0)]
                                                    (condp == marker
                                                      marker-kw    (nth keywords (array-get v 1))
                                                      marker-other (thaw-fn (array-get v 1))
                                                      marker-inf   ##Inf
                                                      marker-minus-inf ##-Inf
                                                      marker-nan   ##NaN
                                                      (db/raise "Unexpected value marker " marker " in " (pr-str v)
                                                        {:error :serialize :value v})))
                                       true (db/raise "Unexpected value type (" (pr-str v) ")"
                                              {:error :serialize :value v}))
                                  tx (+ tx0 (array-get arr 3))]
                              (db/datom e a v tx))))
                    #?(:cljd do :clj arrays/into-array))
         aevt     (some->> (dict-get from "aevt") (amap #(#?(:cljd aget :default arrays/aget) eavt %)) #?(:cljd do :clj arrays/into-array))
         avet     (some->> (dict-get from "avet") (amap #(#?(:cljd aget :default arrays/aget) eavt %)) #?(:cljd do :clj arrays/into-array))
         settings (merge
                    {:branching-factor (dict-get from "branching-factor")
                     :ref-type         (some-> (dict-get from "ref-type") keyword)}
                    (select-keys opts [:branching-factor :ref-type]))]
     (db/restore-db
       {:schema  schema
        :eavt    #?(:cljd (into (sorted-set-by db/cmp-datoms-eavt) eavt)
                    :default (set/from-sorted-array db/cmp-datoms-eavt eavt (arrays/alength eavt) settings))
        :aevt    #?(:cljd (into (sorted-set-by db/cmp-datoms-aevt) aevt)
                    :default (set/from-sorted-array db/cmp-datoms-aevt aevt (arrays/alength aevt) settings))
        :avet    #?(:cljd (into (sorted-set-by db/cmp-datoms-avet) eavt)
                    :default
                    (set/from-sorted-array db/cmp-datoms-avet avet (arrays/alength avet) settings))
        :max-eid (dict-get from "max-eid")
        :max-tx  (dict-get from "max-tx")}))))
