(ns datascript.test.parser
  (:require
   #?(:cljd  [cljd.test :as t :refer        [is are deftest testing]]
      :cljs [cljs.test    :as t :refer-macros [is are deftest testing]]
      :clj  [clojure.test :as t :refer        [is are deftest testing]])
   [datascript.core :as d]
   [datascript.db :as db]
   [datascript.parser :as dp]
   [datascript.test.core :as tdc :refer [thrown-msg?]])
  #?(:cljd (:require [cljd.core :refer [ExceptionInfo]])
     :clj
     (:import [clojure.lang ExceptionInfo])))



(deftest bindings
  (are [form res] (= (dp/parse-binding form) res)
    '?x
    (dp/->BindScalar (dp/->Variable '?x))

    '_
    (dp/->BindIgnore)

    '[?x ...]
    (dp/->BindColl (dp/->BindScalar (dp/->Variable '?x)))

    '[?x]
    (dp/->BindTuple [(dp/->BindScalar (dp/->Variable '?x))])

    '[?x ?y]
    (dp/->BindTuple [(dp/->BindScalar (dp/->Variable '?x)) (dp/->BindScalar (dp/->Variable '?y))])

    '[_ ?y]
    (dp/->BindTuple [(dp/->BindIgnore) (dp/->BindScalar (dp/->Variable '?y))])

    '[[_ [?x ...]] ...]
    (dp/->BindColl
     (dp/->BindTuple [(dp/->BindIgnore)
                      (dp/->BindColl
                       (dp/->BindScalar (dp/->Variable '?x)))]))

    '[[?a ?b ?c]]
    (dp/->BindColl
     (dp/->BindTuple [(dp/->BindScalar (dp/->Variable '?a))
                      (dp/->BindScalar (dp/->Variable '?b))
                      (dp/->BindScalar (dp/->Variable '?c))])))

  (is (thrown-msg? "Cannot parse binding"
                   (dp/parse-binding :key))))

(deftest in
  (are [form res] (= (dp/parse-in form) res)
    '[?x]
    [(dp/->BindScalar (dp/->Variable '?x))]

    '[$ $1 % _ ?x]
    [(dp/->BindScalar (dp/->SrcVar '$))
     (dp/->BindScalar (dp/->SrcVar '$1))
     (dp/->BindScalar (dp/->RulesVar))
     (dp/->BindIgnore)
     (dp/->BindScalar (dp/->Variable '?x))]

    '[$ [[_ [?x ...]] ...]]
    [(dp/->BindScalar (dp/->SrcVar '$))
     (dp/->BindColl
      (dp/->BindTuple [(dp/->BindIgnore)
                       (dp/->BindColl
                        (dp/->BindScalar (dp/->Variable '?x)))]))])

  (is (thrown-msg? "Cannot parse binding"
                   (dp/parse-in ['?x :key]))))

(deftest with
  (is (= (dp/parse-with '[?x ?y])
         [(dp/->Variable '?x) (dp/->Variable '?y)]))

  (is (thrown-msg? "Cannot parse :with clause"
                   (dp/parse-with '[?x _]))))
