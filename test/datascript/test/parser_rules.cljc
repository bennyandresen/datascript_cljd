(ns datascript.test.parser-rules
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

(deftest clauses
  (are [form res] (= (set (dp/parse-rules form)) res)
    '[[(rule ?x)
       [?x :name _]]]
    #{(dp/->Rule
        (dp/->PlainSymbol 'rule)
        [ (dp/->RuleBranch
            (dp/->RuleVars nil [(dp/->Variable '?x)])
            [(dp/->Pattern
               (dp/->DefaultSrc)
               [(dp/->Variable '?x) (dp/->Constant :name) (dp/->Placeholder)])]) ])}))

(deftest rule-vars
  (are [form res] (= (set (dp/parse-rules form)) res)
    '[[(rule [?x] ?y)
       [_]]]
    #{(dp/->Rule
       (dp/->PlainSymbol 'rule)
       [ (dp/->RuleBranch
          (dp/->RuleVars [(dp/->Variable '?x)] [(dp/->Variable '?y)])
          [(dp/->Pattern (dp/->DefaultSrc) [(dp/->Placeholder)])]) ])}

    '[[(rule [?x ?y] ?a ?b)
       [_]]]
    #{(dp/->Rule
       (dp/->PlainSymbol 'rule)

       [ (dp/->RuleBranch
          (dp/->RuleVars [(dp/->Variable '?x) (dp/->Variable '?y)]
                         [(dp/->Variable '?a) (dp/->Variable '?b)])
          [(dp/->Pattern (dp/->DefaultSrc) [(dp/->Placeholder)])]) ])}

    '[[(rule [?x])
       [_]]]
    #{(dp/->Rule
       (dp/->PlainSymbol 'rule)
       [ (dp/->RuleBranch
          (dp/->RuleVars [(dp/->Variable '?x)] nil)
          [(dp/->Pattern (dp/->DefaultSrc) [(dp/->Placeholder)])]) ])})

  (is (thrown-msg? "Cannot parse rule-vars"
                   (dp/parse-rules '[[(rule) [_]]])))

  (is (thrown-msg? "Cannot parse rule-vars"
                   (dp/parse-rules '[[(rule []) [_]]])))

  (is (thrown-msg? "Rule variables should be distinct"
                   (dp/parse-rules '[[(rule ?x ?y ?x) [_]]])))

  (is (thrown-msg? "Rule variables should be distinct"
                   (dp/parse-rules '[[(rule [?x ?y] ?z ?x) [_]]])))
  )

(deftest branches
  (are [form res] (= (set (dp/parse-rules form)) res)
    '[[(rule ?x)
       [:a]
       [:b]]
      [(rule ?x)
       [:c]]]
    #{(dp/->Rule
       (dp/->PlainSymbol 'rule)
       [ (dp/->RuleBranch
          (dp/->RuleVars nil [(dp/->Variable '?x)])
          [(dp/->Pattern (dp/->DefaultSrc) [(dp/->Constant :a)])
           (dp/->Pattern (dp/->DefaultSrc) [(dp/->Constant :b)])])
        (dp/->RuleBranch
         (dp/->RuleVars nil [(dp/->Variable '?x)])
         [(dp/->Pattern (dp/->DefaultSrc) [(dp/->Constant :c)])]) ])}

    '[[(rule ?x)
       [:a]
       [:b]]
      [(other ?x)
       [:c]]]
    #{(dp/->Rule
       (dp/->PlainSymbol 'rule)
       [ (dp/->RuleBranch
          (dp/->RuleVars nil [(dp/->Variable '?x)])
          [(dp/->Pattern (dp/->DefaultSrc) [(dp/->Constant :a)])
           (dp/->Pattern (dp/->DefaultSrc) [(dp/->Constant :b)])]) ])
      (dp/->Rule
       (dp/->PlainSymbol 'other)
       [ (dp/->RuleBranch
          (dp/->RuleVars nil [(dp/->Variable '?x)])
          [(dp/->Pattern (dp/->DefaultSrc) [(dp/->Constant :c)])]) ])}
    )

  (is (thrown-msg? "Rule branch should have clauses"
                   (dp/parse-rules '[[(rule ?x)]])))

  (is (thrown-msg? "Arity mismatch"
                   (dp/parse-rules '[[(rule ?x) [_]]
                                     [(rule ?x ?y) [_]]])))

  (is (thrown-msg? "Arity mismatch"
                   (dp/parse-rules '[[(rule ?x) [_]]
                                     [(rule [?x]) [_]]])))
  )
