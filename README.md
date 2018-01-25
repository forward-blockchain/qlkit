# Qlkit

![alt text](http://cdn-images-1.medium.com/max/400/1*2f0P9H1JHpr3MNQ3SKEWCQ.png)

[Recommended Introductory Article](https://medium.com/p/79b7b118ddac)

Qlkit is a ClojureScript web development framework heavily inspired by OmNext. It relies on React for component rendering and makes use of a query language comparable to GraphQL to encapsulate components and to optimize server calls. It is lightweight, with around 300 lines for the core qlkit library and has no outside dependencies other than React.

Qlkit is designed to be highly composable, and is therefore separated into the following packages:

- `qlkit` contains the core routing and parsing engine
- `qlkit-renderer` is an optional "batteries included" component rendering engine
- `qlkit-material-ui` makes it easy to use [material ui](http://material-ui.com) components in your app

Everyone using these libraries is highly encouraged to read their source code: We have done our best to make sure the code in these libraries is easy to understand.

## Installation

To use qlkit, simply put the following dependency in your project.clj:

```
[qlkit "0.3.0"]
```

Probably the easiest way to use and understand qlkit is to use our [fancy todo demo app](http://github.com/forward-blockchain/qlkit-todo-demo) as your starting point.

For more advanced ClojureScript users, we have also created a [simpler demo app](http://github.com/forward-blockchain/qlkit-todo-demo-raw) with less "sugar" and which uses default React rendering and sablono.

For additional info, please visit our Wiki!
