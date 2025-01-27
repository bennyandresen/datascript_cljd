(ns datascript.test.query-v3
  (:require
   #?(:cljd  [cljd.test :as t :refer        [is are deftest testing]]
      :cljs [cljs.test    :as t :refer-macros [is are deftest testing]]
      :clj  [clojure.test :as t :refer        [is are deftest testing]])
   [datascript.core :as d]
   [datascript.db :as db]
   [datascript.query-v3 :as dq]
   [datascript.test.core :as tdc :refer [thrown-msg?]])
  #?(:cljd (:require [cljd.core :refer [ExceptionInfo]])
     :clj
     (:import [clojure.lang ExceptionInfo])))



(deftest test-validation
  (are [q ins msg] (thrown-msg? msg (apply dq/q q ins))
    '[:find ?a :in $ ?a]          [0]   "Wrong number of arguments for bindings [$ ?a], 2 required, 1 provided"
    '[:find ?a :in $ :where [?a]] [0 1] "Wrong number of arguments for bindings [$], 1 required, 2 provided"
    '[:find ?a :where [?a]]       [0 1] "Wrong number of arguments for bindings [$], 1 required, 2 provided"

    '[:find ?a :where [?a 1]]     [:a]  "Cannot match by pattern [?a 1] because source is not a collection: :a"))

#_(deftest test-query
  (is (= (dq/q '[:find ?a :where [?a ?a]]
               [[1 2] [3 3] [4 5] [6 6]])
         #{[3] [6]})))
