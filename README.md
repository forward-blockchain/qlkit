![alt text](http://cdn-images-1.medium.com/max/400/1*2f0P9H1JHpr3MNQ3SKEWCQ.png)

[Recommended Introductory Article](https://medium.com/p/79b7b118ddac)

Qlkit is a ClojureScript web development framework inspired by OmNext. It relies on React for component rendering and makes use of a query language comparable to GraphQL to encapsulate components and to optimize server calls. It is lightweight, with around 300 lines of code in the core qlkit library and no outside dependencies (besides React).

Qlkit is designed to be highly composable, and is therefore separated into the following packages:

- `qlkit` contains the core routing and parsing engine
- [`qlkit-renderer`](http://github.com/forward-blockchain/qlkit-renderer) is an optional "batteries included" component rendering engine
- [`qlkit-material-ui`](http://github.com/forward-blockchain/qlkit-material-ui) makes it easy to use [material ui](http://material-ui.com) components in your app

Everyone using these libraries is highly encouraged to read their source code: We have done our best to make sure the code in these libraries is easy to understand.

## Installation

*Be aware that qlkit is still alpha software at the moment, do not yet rely on it for production development!*

To use qlkit, simply put the following dependency in your project.clj:

```
[qlkit "0.3.0-SNAPSHOT"]
```

The easiest way to use and understand qlkit is to rely on our [fancy todo demo app](http://github.com/forward-blockchain/qlkit-todo-demo) as your starting point.

For more advanced ClojureScript users, we have also created a [simpler demo app](http://github.com/forward-blockchain/qlkit-todo-demo-raw) with less "sugar" and which uses default React rendering and sablono.

For additional info, [please visit our Wiki](https://github.com/forward-blockchain/qlkit/wiki)!


---
_Copyright (c) Conrad Barski. All rights reserved._
_The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php), the same license used by Clojure._
