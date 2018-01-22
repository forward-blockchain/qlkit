# Qlkit Query Syntax

A qlkit query is a vector of queries, where each query is a vector of a namespaced `keyword`, an optional parameters `map`, and zero or more child queries.

This is a valid qlkit query:
````Clojure
[[:fruit/name]]
````

Here's another one:
````Clojure
[[:fruit/name {:fruit/count 1}]]
````

Here's a query with children:
````Clojure
[[:ex/fruits {}
  [:fruit/name {}]
  [:fruit/count {}]]]
````
Child queries can be nested indefinitely:
````Clojure
[[:ex/nuts {}
  [:nut/name {}]
  [:nut/count {}]
  [:nut/flavors {}
    [:flavor/bitter {:min 5}]
    [:flavor/salty]]]]
````

For convenience a query may contain `nil` queries (that will be elided), or `seq`s (that will be spliced into the query).

### Nil queries are elided
These queries are identical:
````
[[:fruit/name] nil]
[[:fruit/name]]
````

### Seqs are spliced into the query
The values of a `seq` contained in a query will be spliced in. This allows for calling a function that returns a child query.

These queries are identical:
````
[[:ex/fruits [:fruit/name] [:fruit/count]]
[[:ex/fruits '([:fruit/name] [:fruit/count])]
````

### Clojure Spec
````Clojure
(require '[clojure.spec.alpha :as s])

(s/def ::query
  (s/spec (s/and vector? (s/cat :query (s/* ::term)))))

(s/def ::term
  (s/spec (s/and vector?
                 (s/cat
                  :tag keyword?
                  :attrs (s/? map?)
                  :children (s/* ::term)))))
````
