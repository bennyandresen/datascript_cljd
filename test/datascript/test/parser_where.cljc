(ns datascript.test.parser-where
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

(deftest pattern
  (are [clause pattern] (= (dp/parse-clause clause) pattern)
    '[?e ?a ?v]
    (dp/->Pattern (dp/->DefaultSrc) [(dp/->Variable '?e) (dp/->Variable '?a) (dp/->Variable '?v)])

    '[_ ?a _ _]
    (dp/->Pattern (dp/->DefaultSrc) [(dp/->Placeholder) (dp/->Variable '?a) (dp/->Placeholder) (dp/->Placeholder)])

    '[$x _ ?a _ _]
    (dp/->Pattern (dp/->SrcVar '$x) [(dp/->Placeholder) (dp/->Variable '?a) (dp/->Placeholder) (dp/->Placeholder)])

    '[$x _ :name ?v]
    (dp/->Pattern (dp/->SrcVar '$x) [(dp/->Placeholder) (dp/->Constant :name) (dp/->Variable '?v)])

    '[$x _ sym ?v]
    (dp/->Pattern (dp/->SrcVar '$x) [(dp/->Placeholder) (dp/->Constant 'sym) (dp/->Variable '?v)])

    '[$x _ $src-sym ?v]
    (dp/->Pattern (dp/->SrcVar '$x) [(dp/->Placeholder) (dp/->Constant '$src-sym) (dp/->Variable '?v)]))

  (is (thrown-msg? "Pattern could not be empty"
                   (dp/parse-clause '[])))
  )

(deftest test-pred
  (are [clause res] (= (dp/parse-clause clause) res)
    '[(pred ?a 1)]
    (dp/->Predicate (dp/->PlainSymbol 'pred) [(dp/->Variable '?a) (dp/->Constant 1)])

    '[(pred)]
    (dp/->Predicate (dp/->PlainSymbol 'pred) [])

    '[(?custom-pred ?a)]
    (dp/->Predicate (dp/->Variable '?custom-pred) [(dp/->Variable '?a)])
))

(def ->Fn
  #?(:cljd dp/->FunctionCall :default dp/->Function))

(deftest test-fn
  (are [clause res] (= (dp/parse-clause clause) res)
    '[(fn ?a 1) ?x]
    (->Fn (dp/->PlainSymbol 'fn) [(dp/->Variable '?a) (dp/->Constant 1)] (dp/->BindScalar (dp/->Variable '?x)))

    '[(fn) ?x]
    (->Fn (dp/->PlainSymbol 'fn) [] (dp/->BindScalar (dp/->Variable '?x)))

    '[(?custom-fn) ?x]
    (->Fn (dp/->Variable '?custom-fn) [] (dp/->BindScalar (dp/->Variable '?x)))

    '[(?custom-fn ?arg) ?x]
    (->Fn (dp/->Variable '?custom-fn) [(dp/->Variable '?arg)] (dp/->BindScalar (dp/->Variable '?x)))))

(deftest rule-expr
  (are [clause res] (= (dp/parse-clause clause) res)
    '(friends ?x ?y)
    (dp/->RuleExpr (dp/->DefaultSrc) (dp/->PlainSymbol 'friends) [(dp/->Variable '?x) (dp/->Variable '?y)])

    '(friends "Ivan" _)
    (dp/->RuleExpr (dp/->DefaultSrc) (dp/->PlainSymbol 'friends) [(dp/->Constant "Ivan") (dp/->Placeholder)])

    '($1 friends ?x ?y)
    (dp/->RuleExpr (dp/->SrcVar '$1) (dp/->PlainSymbol 'friends) [(dp/->Variable '?x) (dp/->Variable '?y)])

    '(friends something)
    (dp/->RuleExpr (dp/->DefaultSrc) (dp/->PlainSymbol 'friends) [(dp/->Constant 'something)]))

  (is (thrown-msg? "rule-expr requires at least one argument"
                   (dp/parse-clause '(friends)))))

(deftest not-clause
  (are [clause res] (= (dp/parse-clause clause) res)
    '(not [?e :follows ?x])
    (dp/->Not
     (dp/->DefaultSrc)
     [(dp/->Variable '?e) (dp/->Variable '?x)]
     [ (dp/->Pattern
        (dp/->DefaultSrc)
        [(dp/->Variable '?e) (dp/->Constant :follows) (dp/->Variable '?x)]) ])

    '(not
      [?e :follows ?x]
      [?x _ ?y])
    (dp/->Not
     (dp/->DefaultSrc)
     [(dp/->Variable '?e) (dp/->Variable '?x) (dp/->Variable '?y)]
     [ (dp/->Pattern
        (dp/->DefaultSrc)
        [(dp/->Variable '?e) (dp/->Constant :follows) (dp/->Variable '?x)])
      (dp/->Pattern
       (dp/->DefaultSrc)
       [(dp/->Variable '?x) (dp/->Placeholder) (dp/->Variable '?y)])])

    '($1 not [?x])
    (dp/->Not
     (dp/->SrcVar '$1)
     [(dp/->Variable '?x)]
     [ (dp/->Pattern (dp/->DefaultSrc) [(dp/->Variable '?x)]) ])

    '(not-join [?e ?y]
               [?e :follows ?x]
               [?x _ ?y])
    (dp/->Not
     (dp/->DefaultSrc)
     [(dp/->Variable '?e) (dp/->Variable '?y)]
     [ (dp/->Pattern
        (dp/->DefaultSrc)
        [(dp/->Variable '?e) (dp/->Constant :follows) (dp/->Variable '?x)])
      (dp/->Pattern
       (dp/->DefaultSrc)
       [(dp/->Variable '?x) (dp/->Placeholder) (dp/->Variable '?y)])])

    '($1 not-join [?e] [?e :follows ?x])
    (dp/->Not
     (dp/->SrcVar '$1)
     [(dp/->Variable '?e)]
     [ (dp/->Pattern
        (dp/->DefaultSrc)
        [(dp/->Variable '?e) (dp/->Constant :follows) (dp/->Variable '?x)]) ])
    )

  (is (thrown-msg? "Join variables should not be empty"
                   (dp/parse-clause '(not-join [] [?y]))))

  (is (thrown-msg? "Join variables should not be empty"
                   (dp/parse-clause '(not [_]))))

  (is (thrown-msg? "Cannot parse 'not-join' clause"
                   (dp/parse-clause '(not-join [?x]))))

  (is (thrown-msg? "Cannot parse 'not' clause"
                   (dp/parse-clause '(not))))
  )

(deftest or-clause
  (are [clause res] (= (dp/parse-clause clause) res)
    '(or [?e :follows ?x])
    (dp/->Or
     (dp/->DefaultSrc)
     (dp/->RuleVars nil [(dp/->Variable '?e) (dp/->Variable '?x)])
     [ (dp/->Pattern
        (dp/->DefaultSrc)
        [(dp/->Variable '?e) (dp/->Constant :follows) (dp/->Variable '?x)]) ])

    '(or
      [?e :follows ?x]
      [?e :friend ?x])
    (dp/->Or
     (dp/->DefaultSrc)
     (dp/->RuleVars nil [(dp/->Variable '?e) (dp/->Variable '?x)])
     [ (dp/->Pattern
        (dp/->DefaultSrc)
        [(dp/->Variable '?e) (dp/->Constant :follows) (dp/->Variable '?x)])
      (dp/->Pattern
       (dp/->DefaultSrc)
       [(dp/->Variable '?e) (dp/->Constant :friend) (dp/->Variable '?x)])])

    '(or
      [?e :follows ?x]
      (and
       [?e :friend ?x]
       [?x :friend ?e]))
    (dp/->Or
     (dp/->DefaultSrc)
     (dp/->RuleVars nil [(dp/->Variable '?e) (dp/->Variable '?x)])
     [ (dp/->Pattern
        (dp/->DefaultSrc)
        [(dp/->Variable '?e) (dp/->Constant :follows) (dp/->Variable '?x)])
      (dp/->And
       [(dp/->Pattern
         (dp/->DefaultSrc)
         [(dp/->Variable '?e) (dp/->Constant :friend) (dp/->Variable '?x)])
        (dp/->Pattern
         (dp/->DefaultSrc)
         [(dp/->Variable '?x) (dp/->Constant :friend) (dp/->Variable '?e)])]) ])

    '($1 or [?x])
    (dp/->Or
     (dp/->SrcVar '$1)
     (dp/->RuleVars nil [(dp/->Variable '?x)])
     [ (dp/->Pattern (dp/->DefaultSrc) [(dp/->Variable '?x)]) ])

    '(or-join [?e]
              [?e :follows ?x]
              [?e :friend ?y])
    (dp/->Or
     (dp/->DefaultSrc)
     (dp/->RuleVars nil [(dp/->Variable '?e)])
     [ (dp/->Pattern
        (dp/->DefaultSrc)
        [(dp/->Variable '?e) (dp/->Constant :follows) (dp/->Variable '?x)])
      (dp/->Pattern
       (dp/->DefaultSrc)
       [(dp/->Variable '?e) (dp/->Constant :friend) (dp/->Variable '?y)])])

    '(or-join [[?e]]
              (and [?e :follows ?x]
                   [?e :friend ?y]))
    (dp/->Or
     (dp/->DefaultSrc)
     (dp/->RuleVars [(dp/->Variable '?e)] nil)
     [ (dp/->And
        [(dp/->Pattern
          (dp/->DefaultSrc)
          [(dp/->Variable '?e) (dp/->Constant :follows) (dp/->Variable '?x)])
         (dp/->Pattern
          (dp/->DefaultSrc)
          [(dp/->Variable '?e) (dp/->Constant :friend) (dp/->Variable '?y)])]) ])

    '($1 or-join [[?e] ?x]
         [?e :follows ?x])
    (dp/->Or
     (dp/->SrcVar '$1)
     (dp/->RuleVars [(dp/->Variable '?e)] [(dp/->Variable '?x)])
     [ (dp/->Pattern
        (dp/->DefaultSrc)
        [(dp/->Variable '?e) (dp/->Constant :follows) (dp/->Variable '?x)]) ])
    )

  (is (thrown-msg? "Cannot parse rule-vars"
                   (dp/parse-clause '(or-join [] [?y]))))

  (is (thrown-msg? "Join variables should not be empty"
                   (dp/parse-clause '(or [_]))))

  (is (thrown-msg? "Cannot parse 'or-join' clause"
                   (dp/parse-clause '(or-join [?x]))))

  (is (thrown-msg? "Cannot parse 'or' clause"
                   (dp/parse-clause '(or))))
  )
