(ns qlkit.qlkit-test
  (:refer-clojure :rename {read core-read sync core-sync})
  (:require [clojure.test :refer [deftest is]]
            [qlkit.core :as ql]))

(deftest actualize-test []
  ;;non-sequences evaluate to themselves
  (is (= (#'ql/actualize 1) 1))
  (let [x (atom 0)
        coll (for [_ (range 10)]
               (swap! x inc))]
    ;;coll is still lazy
    (is (= @x 0))
    (#'ql/actualize coll)
    ;;coll is fully evaluated
    (is (= @x 10))))

(defmulti read   (fn [a b c]   (first a)))
(defmulti mutate (fn [a b c]   (first a)))
(defmulti remote (fn [a b]     (first a)))
(defmulti sync   (fn [a b c d] (first a)))

(defn parse-with [fun query-term]
  (remove-all-methods read)
  (remove-all-methods mutate)
  (remove-all-methods remote)
  (fun)
  (#'ql/parse-query-term query-term {}))

(deftest parse-query-test []
  (reset! ql/mount-info {:parsers {:read   read
                                   :mutate mutate
                                   :remote remote}
                         :state (atom {})})
  ;;a read parser result is returned
  (is (= (parse-with (fn []
                       (defmethod read :foo
                         [query-term env state]
                         42)
                       
                       )
                     [:foo])
         42))
  ;;a mutate function returns a result, but also performs mutations
  (let [x (atom 0)]
    (parse-with (fn []
                  (defmethod mutate :bar!
                    [query-term env state]
                    (swap! x inc)))
                [:bar!])
    (is (= @x 1)))
  ;;If no parser is provided, an error is thrown
  (is (thrown-with-msg? clojure.lang.ExceptionInfo
                        #"no parser for :foo"
                        (parse-with (fn [])
                                    [:foo])))
  ;;If there's a remote parser, it's OK to not have other parsers
  (is (nil? (parse-with (fn []
                          (defmethod remote :foo
                            [query-term state]
                            33))
                        [:foo])))
  ;;A parser can call parse children for recursive parsing
  (is (= (map #(dissoc % ::ql/env)
              (parse-with (fn []
                            (defmethod read :animals
                              [query-term env state]
                              (for [animal-id (range 3)]
                                (ql/parse-children query-term (assoc env :animal-id animal-id))))
                            (defmethod read :name
                              [query-term env state]
                              ({0 :duck 1 :cat 2 :dog} (:animal-id env))))
                          [:animals {} [:name]]))
         [{:name :duck} {:name :cat} {:name :dog}])))

(deftest camel-case-test []
  (is (= (#'ql/camel-case "foo-bar")
         "fooBar")))

(deftest camel-case-keys-test []
  (is (= (#'ql/camel-case-keys {:foo 1
                              "bar" 2
                              :foo-bar 3
                              :fooDerp 4
                              33 5})
         {:foo 1, "bar" 2, :fooBar 3, :fooDerp 4, 33 5})))

(deftest fix-event-references-test []
  ;;If we bind 4 to the *this* dynvar, we can override this value with a value handed to the fix-event-references function
  (binding [ql/*this* 4]
    (let [result      (atom nil)
          props       {:foo (fn []
                              (reset! result ql/*this*))}
          fixed-props (#'ql/fix-event-references 5 props)]
      ((:foo fixed-props))
      (is (= @result 5)))))

(deftest fix-classname-test []
  (is (= (#'ql/fix-classname {:class "foo"})
         {:className "foo"}))
  (is (= (#'ql/fix-classname {:foo "foo"})
         {:foo "foo"})))

(deftest splice-in-seqs-test []
  (is (= (#'ql/splice-in-seqs [:foo (list :bar :baz)])
         [:foo :bar :baz])
      (= (#'ql/splice-in-seqs [:foo nil :baz])
         [:foo :baz])))

(deftest normalize-query-test []
  (is (= (#'ql/normalize-query [[:foo [:bar]] [:baz]])
         [[:foo {} [:bar {}]] [:baz {}]]))
  (is (= (#'ql/normalize-query [[:foo {} (list [:bar {} [:qux nil (list [:a] [:b])]])] [:baz]])
         [[:foo {} [:bar {} [:qux {} [:a {}] [:b {}]]]] [:baz {}]])))

(deftest add-class-test []
  ;;all a qlkit class needs is a render function
  (let [fun (fn [])]
    (reset! ql/classes {})
    (#'ql/add-class :foo {:render fun})
    (is (= @ql/classes
           {:foo {:render fun}})))
  ;;render function missing :(
  (let [fun (fn [])]
    (reset! ql/classes {})
    (is (thrown-with-msg? java.lang.AssertionError
                          #"Assert failed: \(:render class\)"
                          (#'ql/add-class :foo {:bar fun}))))
 ;;a query can uptionally be added to the class
  (let [fun (fn [])]
    (reset! ql/classes {})
    (#'ql/add-class :foo {:render fun
                          :query [[:foo]]})
    (is (= @ql/classes
           {:foo {:render fun
                  :query [[:foo {}]]}})))
  ;;query has to match clojure.spec declaration
  (let [fun (fn [])]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"Invalid query: \n\[\[\"foo\" \{\}\]]\nIn: \[0 0\] val: \"foo\" fails spec: :qlkit.spec/query-term at: \[:query :tag\] predicate: keyword\?\n"
                          (reset! ql/classes {})
                          (#'ql/add-class :foo {:render fun
                                                :query [["foo"]]})))))

(deftest get-query-test []
  (let [fun (fn [])]
    (reset! ql/classes {})
    (#'ql/add-class :foo {:render fun
                          :query [[:foo]]})
    (is (= (ql/get-query :foo)
           [[:foo {}]]))))

(deftest mutation-query-test []
  (is (= (#'ql/mutation-query-term? [:foo])
         false)
      (= (#'ql/mutation-query-term? [:foo!])
         true)))

(defn parse-remote-with [fun query]
  (remove-all-methods remote)
  (fun)
  (#'ql/parse-query-remote query))

(deftest parse-query-remote-test []
  (reset! ql/mount-info {:parsers {:remote remote}
                         :state (atom nil)})
  ;;If the remote returns the query, then we get our query back
  (is (= (parse-remote-with (fn []
                       (defmethod remote :foo
                         [query state]
                         query))
                            [[:foo]])
         [[:foo {}]]))
  ;;If there are no remotes, we just get an empty seq
  (is (= (parse-remote-with (fn [])
                            [[:foo]])
         ()))
  ;;We can parse child queries when parsing a remote query, and parsing functions can modify the query
  (is (= (parse-remote-with (fn []
                              (defmethod remote :foo
                                [query state]
                                (ql/parse-children-remote query))
                              (defmethod remote :bar
                                [query state]
                                [:bar {:baz 42}]))
                            [[:foo {} [:bar]]])
         [[:foo {} [:bar {:baz 42}]]])))

(defn parse-sync-with [fun query-term result]
  (remove-all-methods sync)
  (fun)
  (#'ql/parse-query-term-sync query-term result {}))

(deftest parse-query-term-sync-test []
  (let [state (atom nil)]
    (reset! ql/mount-info {:parsers {:sync sync}
                           :state state})
    ;;The sync merges the result into the state
    (parse-sync-with (fn []
                       (defmethod sync :foo
                         [query-term result env state-atom]
                         (reset! state-atom result)))
                     [:foo {}]
                     42)
    (is (= @state 42))
    ;;If a read query is missing a sync, an error is thrown
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"Missing sync parser for :foo"
                          (parse-sync-with (fn [])
                                           [:foo {}]
                                           42)))
    ;;Remote mutations are permitted without a sync parser
    (parse-sync-with (fn [])
                     [:foo! {}]
                     42)
    ;;Here we are calling child sync functions recursively. Note that lasy seqs will be immediately be made un-lazy by qlkit.
    (reset! state {})
    (parse-sync-with (fn []
                       (defmethod sync :foo
                         [query-term result env state-atom]
                         (map-indexed (fn [index item]
                                        (ql/parse-children-sync query-term item (assoc env :id index)))
                                      result))
                       (defmethod sync :bar
                         [query-term result env state-atom]
                         (swap! state-atom assoc (:id env) result)))
                     [:foo {} [:bar]]
                     [{:bar :red} {:bar :green} {:bar :blue}])
    (is (= @state {0 :red 1 :green 2 :blue}))))

(deftest map-delta-test []
  (let [check (fn [map1 map2]
                (is (= (merge map1 (#'ql/map-delta map1 map2)) (merge map1 map2))))]
    (check {:a 1} {:b 2})
    (check {:a 1} {:a 2 :b 2})
    (check {:a 1 :b 2} {:a 2 :b 2})
    (is (= (#'ql/map-delta {:a 1} {:a 1})
           {}))))

(deftest root-query-test []
  ;;If we're at the root level and the environment is empty, just evaluates to itself
  (is (= (#'ql/root-query {} [[:foo]])
         [[:foo]]))
  ;;Here, the :bar parser set an env variable of id=55, need to add that to the root query
  (is (= (#'ql/root-query {::ql/parent-env {::ql/query-key :bar
                                            :id            55}}
                          [[:foo]])
         [[:bar {:id 55} [:foo]]]))
  ;;If the environment has nested parent environments, all env variables end up in the query, but with duplication removed
  (is (= (#'ql/root-query {::ql/parent-env {::ql/query-key  :bar
                                            ::ql/parent-env {::ql/query-key :baz
                                                             :id-b          66
                                                             :id-a          77}
                                            :id-a           55
                                            :id-b           66}}
                          [[:foo]])
         [[:baz {:id-b 66, :id-a 77} [:bar {:id-a 55} [:foo]]]])))

(deftest gather-style-props-test []
  ;;Official dom style elements are gathered into style map
  (is (= (#'ql/gather-style-props {:color :blue :foo 5})
         {:foo 5
          :style {:color :blue}}))
  ;;Can override this behavior by using string keys
  (is (= (#'ql/gather-style-props {"color" :blue :foo 5})
         {:foo 5
          "color" :blue})))

(deftest mount-test []
  (ql/mount {:state (atom 5)})
  (is (= @(:state @ql/mount-info) 5)))

(deftest perform-remote-query-test []
  (let [state (atom {:foos {}})]
    (ql/mount {:remote-handler (fn [query callback]
                                 (callback [{:bar 3 :baz 42}]))
               :parsers        {:sync sync}
               :state          state})
    (remove-all-methods sync)
    (defmethod sync :foo
      [query-term result env state-atom]
      (ql/parse-children-sync query-term result (assoc env :foo-id 7)))
    (defmethod sync :bar
      [query-term result env state-atom]
      (swap! state-atom assoc-in [:foos (:foo-id env) :bar] result))
    (defmethod sync :baz
      [query-term result env state-atom]
      (swap! state-atom assoc-in [:foos (:foo-id env) :baz] result))
    (ql/perform-remote-query [[:foo {} [:bar] [:baz]]])
    (is (= @state {:foos {7 {:bar 3, :baz 42}}}))))

(deftest transact!-test []
  (let [state (atom {})]
    (remove-all-methods sync)
    (remove-all-methods read)
    (remove-all-methods mutate)
    (remove-all-methods remote)
    (defmethod read :foo
      [query-term env state]
      42)
    (defmethod remote :foo
      [query-term state]
      query-term)
    (defmethod sync :foo
      [query-term result env state-atom]
      (swap! state-atom assoc :foo result))
    (ql/mount {:remote-handler (fn [query callback]
                                  (callback [:yup]))
                :parsers        {:sync sync
                                 :read read
                                 :mutate mutate
                                 :remote remote}
                :state          state})
    (ql/transact! [:foo])
    (is (= @state {:foo :yup}))))
