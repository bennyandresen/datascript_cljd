(ns datascript.test.validation
  (:require
   #?(:cljd  [cljd.test :as t :refer        [is are deftest testing]]
      :cljs [cljs.test    :as t :refer-macros [is are deftest testing]]
      :clj  [clojure.test :as t :refer        [is are deftest testing]])
   [datascript.core :as d]
   [datascript.test.core :as tdc :refer [thrown-msg?]]))

#?(:cljs
   (def Throwable js/Error))

#_(deftest test-with-validation
  (let [db (d/empty-db {:profile {:db/valueType :db.type/ref}
                        :id {:db/unique :db.unique/identity}})]
    (are [tx] (thrown-with-msg? #?(:cljd dynamic :default Throwable) #"Expected number, string or lookup ref for :db/id" (d/db-with db tx))
      [{:db/id #"" :name "Ivan"}])

    (are [tx] (thrown-with-msg? #?(:cljd dynamic :default Throwable) #"Bad entity attribute" (d/db-with db tx))
      [[:db/add -1 nil "Ivan"]]
      [[:db/add -1 17 "Ivan"]]
      [{:db/id -1 17 "Ivan"}])

    (are [tx] (thrown-with-msg? #?(:cljd dynamic :default Throwable) #"Cannot store nil as a value" (d/db-with db tx))
      [[:db/add -1 :name nil]]
      [{:db/id -1 :name nil}]
      [[:db/add -1 :id nil]]
      [{:db/id -1 :id "A"}
       {:db/id -1 :id nil}])

    (are [tx] (thrown-with-msg? #?(:cljd dynamic :default Throwable) #"Expected number or lookup ref for entity id" (d/db-with db tx))
      [[:db/add nil :name "Ivan"]]
      [[:db/add {} :name "Ivan"]]
      [[:db/add -1 :profile #"regexp"]]
      [{:db/id -1 :profile #"regexp"}])

    (is (thrown-with-msg? #?(:cljd dynamic :default Throwable) #"Unknown operation" (d/db-with db [["aaa" :name "Ivan"]])))
    (is (thrown-with-msg? #?(:cljd dynamic :default Throwable) #"Bad entity type at" (d/db-with db [:db/add "aaa" :name "Ivan"])))
    (is (thrown-with-msg? #?(:cljd dynamic :default Throwable) #"Tempids are allowed in :db/add only" (d/db-with db [[:db/retract -1 :name "Ivan"]])))
    (is (thrown-with-msg? #?(:cljd dynamic :default Throwable) #"Bad transaction data" (d/db-with db {:profile "aaa"})))))

(deftest test-unique
  (let [db (d/db-with (d/empty-db {:name {:db/unique :db.unique/value}})
                      [[:db/add 1 :name "Ivan"]
                       [:db/add 2 :name "Petr"]])]
    (are [tx] (thrown-msg? "unique constraint" (d/db-with db tx))
      [[:db/add 3 :name "Ivan"]]
      [{:db/add 3 :name "Petr"}])
    (d/db-with db [[:db/add 3 :name "Igor"]])
    (d/db-with db [[:db/add 3 :nick "Ivan"]])))
