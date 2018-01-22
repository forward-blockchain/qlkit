# Qlkit

Qlkit is a clojurescript web development framework heavily inspired by OmNext. It relies on react for component rendering and makes use of a query language comparable to GraphQL to encapsulate components and to optimize server calls. It has less than 400 lines of core code and has no outside dependencies other than React.

# Differences from OmNext

## Render function HTML syntax

Qlkit returns HTML in the form as an edn data structure, similar to clojure [hiccup](https://github.com/weavejester/hiccup):

```
[:div [:button {:label "Submit"}]]
```

Qlkit also elides nil children and expands seqs just like hiccup, so the following two expressions are equivalent:

```
[:div (list [:button {:label "Submit"}] [:button {:label "Cancel"}]) nil]

[:div [:button {:label "Submit"}] [:button {:label "Cancel"}]]
```

(Note that qlkit does not support the `id` and `class` declaration sugar of hiccup, since these attributes are deemphasized in modern virtual-dom based web development)

Qlkit automatically substitutes common controls such as `button`s and `input`s with modernized versions of those controls as made available by the material-ui library. The full list of substitutions can be seen in `qlkit.qlkit/component-registry` and can be overridden via `qlkit.qlkit/register-component`.

One additional convenience feature in Qlkit is that html styles can be specified directly at the attribute level, so these two expressions are equivalent:

```
[:button {:margin "2rem" :label "Submit"}]

[:button {:style {margin "2rem"} :label "Submit"}]

```

Qlkit accomplishes this by recognizing all legal `style` attributes and automatically moving those into the style declaration- So don't define attributes for your custom components that mirror legal CSS attributes (i.e. avoid attribute names like `color` or `left` or `width`, which probably are bad names for custom attributes anyway, precisely because they can be confused for CSS attributes.) If you absolutely HAVE TO refer to a mirroring attribute, simply declare the attribute as a string and it will not be treated as a CSS attribute (i.e. use `"width"` instead of `:width`)

As discussed, the output of a renderer should be a pure edn data structure, which is desirable for easy unit testing. The only exception is DOM event handlers, which are declared as functions. When a DOM element keyword is namespaced it is assumed to be a user-declared component via the `ql/component` macro: This means that a keyword like `:foo/Bar` is assumed to be a user-defined component- Note that the `ql/component` macro also will define a symbol `Bar` and set it equal to `:foo/Bar`, for convenience.

Qlkit does not distinguish between computed attributes and "props" as OmNext does. Instead, all attributes can be passed to the child, such as `[MyComponent {:color "blue" :computed-value 3423}]`. (OmNext distinguishes between these two types of "attributes" for optimization reasons, but qlkit prioritizes simplicity over performance.)

## Query Syntax

A "query" item in qlkit is always a vector containing a keyword: 

```
[:person/name]
```

A data-aware component defines a collection of queries, which will look as follows:

```
[[:person/name] [:person/age] [:person/address]]
```

Note that in OmNext, query items are (optionally) naked keyword symbols, so such a query could be written as `[:person/name :person/age :person/address]`, but this is NOT permitted in qlkit, the query items MUST be in a vector. This is necessary because qlkit (unlike OmNext) does not implement a separate ast query syntax for parsing- The base query syntax essentially is already in the form of an ast.

A query item can also have parameters and children- Note how this looks exactly the same as the qlkit HTML syntax:

```
[:persons/search {:name-prefix "smit"} [:person/name] [:person/age]]
```

As with the HMTL syntax, the query syntax also supports sequence expansion, making this equivalent to the previous query item:

```
[:persons/search {:name-prefix "smit"} (list [:person/name] [:person/age])]
```

This feature is used for importing query dependencies of child components, making a syntax like the following possible:

```
[:persons/search {:name-prefix "smit"} (ql/get-query PersonRow)]
```

In qlkit, mutation query items are keywords, just as read query items, but they must end in an exclamation mark, such as `:example/set-number!` (This differs from OmNext, where mutations must be symbols instead of keywords)

## The `qlkit.qlkit/component` Macro

New user-defined components are declared with the `component` macro. It differs from `OmNext/defui` in the following ways:

1. State and query initializations are done by declaring data structures using the `state` and `query-coll` identifiers (OmNext differs by requiring the user to provide _functions_, not _structures_)
1. Component functions are not given a copy of the `this` parameter, as it is usually not needed in qlkit. (In the rare cases where the `this` parameter is needed it can still be accessed via `qlkit.qlkit/*this*`)
1. The `update-state!` and `transact!` functions in qlkit are always assumed to refer to the current component (which is why the `this` parameter is typically not needed.)

## Synchronous Parsers in qlkit

Qlkit supports `read` and `mutate` parsers, just like OmNext. However, unlike with OmNext, the `read` parsers always return a value (as opposed to OmNext, where they return a `:remote`/`:action`/`:value` map) and the `mutate` parsers are assumed to perform a side effect without a meaningful return value. If a parser has a remote component (i.e. it needs to query the server or needs to interact with a blockchain) this is NOT done through the `read`/`mutate` parsers, but is instead done via an additional parser called the `remote` parser.

When a parser is called in qlkit it does not receive params or a dispatch key, as in OmNext: Instead, it just receives a copy of the query that triggered the parser- Note that this query will always be normalized in the form `[:my/key {:some :params} :child1 :child2]` so it's easy to extract the dispatch-key via `(first query`) or the params via `(second query)` or extract the children via `(drop 2 query)`.

The return value for a `remote` parser should be the remote query, which may or may not be different than the local query. (This differs from OmNext where the `:remote` declaration may simply be set to `true` instead of a full query, which causes OmNext to default to the local query) The usual appropriate design pattern is to make remote queries as simple as possible, so the remote parsers should be written to remove extraneous nodes from the query and strip it down as small as possible (usually by calling additional `remote` parsers that also simplify children in the larger query)

Note that a `remote` parser has the option of returning >1 query items, which is sometimes necessary for eliding branch nodes from the query. This is accomplished by returning a sequence (which will be spliced into the parent query, exactly as expected from our earlier discussion of the query syntax.)

The last synchronous parser type is the `sync` parser, which is called when a result is returned by the asynchronous parsers (such as when a server result has been received or a blockchain query has completed). The job a `sync` parser is to update the local state atom to reflect data received from the server/blockchain. Note that all `read` queries with a `remote` declaration MUST have a `sync` declaration (i.e. if you ask for data from the server you must explain what to do when that data is received) or an exception is thrown, but `mutate` queries with `remote` declarations do not need to have a `sync` parser (but are still permitted to optionally provide one).

IMPORTANT: It is usually a bad design to have a `sync` parser for a remote mutation- Instead, simply bundle the remote mutation with a read to synchronize the local state, such as `(transact! [[example/set-number! 22] [example/number]])`- These will both be sent to the server/asyncparser at the same time and can be handled efficiently. The sole exception for this is if the local app had to declare a tempid for a record, which needs to be linked with the permanent ID after the mutation has completed: In that case, a `sync` parser for a mutation is appropriate.

## Asynchronous Parsers

For server-side queries or blockchain queries, we define a separate set of `read` and `mutate` parsers (but no `remote` or `sync` parsers). In the case of in-client blockchain queries, we wrap these in `go` blocks (to guarantee deterministic sequencing of blockchain operations) and compose the with the `qlkit.async/async-transact!` function.

In the same way as with synchronous parsers, the `read`s should return a value, and the `mutate`s should return nothing UNLESS a mutation is resolving tempids, in which case they should return an association table for the tempids, which will be accessible by the `sync` parser for the mutation.

## Understanding The Rerendering Workflow for a Transaction

If we run a transaction such as `(ql/transact! [:todo/new! {:text "Wash dishes"}])` there are some subtleties in how data is refreshed and how the UI is rerendered. Let's walk through these subtleties:

1. *All immediate client state changes are guaranteed to be rerendered in the UI*: Our mutation handler for `:todo/new!` will likely modify local state. Qlkit will force a refresh of the entire UI as soon as the mutations are completed to make sure these state changes are all reflected in the UI.
2. *If a transaction could potentially invalidate additional data after being sent to the server, you can request additional server data fetches inside the transaction*: For instance, suppose the server for a todo app calculates a special "progress" number that is displayed in the UI and may change in value when a new todo item is added- In that case, we could write our transaction as `(ql/transact! [:todo/new! {:text "Wash dishes"}] [:todo/progress])` which essentially says "Send a message to the server to add a new todo item, then tell the server to send back the latest progress." These two query terms will be sent to the server in a single message, but the server will process the individual terms serially: i.e. it will only calculate the progress _after_ the new todo item has been added.
3. *If a transaction is created in the context of a specific component, that component will automatically refetch data from the server as part of the transaction*: In the majority of the cases, when a remote mutation requires data to be refetched, that data relates to the qlkit component that triggered the transaction. Because of this, qlkit will automatically enhance any transaction fired from within a specific component to also fetch all data relating to that component. For instance, let's say we have a simple "Counter" component that has a component query of `[[:count/value]]` and the user clicks on the component to increase the count with the transaction `(ql/transact! [:count/increase!])`. In that case, qlkit will automatically enhance this transaction as if it was `(ql/transact! [:count/increase!] [:count/value])`- This means that if the counter value is managed by the server the value will be automatically refetched. In short, this means that that you usually don't need to explicitly refetch data as described in #2.
