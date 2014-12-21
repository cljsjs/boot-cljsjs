# boot-cljsjs

This project provides tasks for the [boot][boot] build system to package and
use Javascript dependencies in Clojurescript projects:

- `cljsjs-jar` in [`cljsjs.packaging`][cljsjs-packaging] helps to create jars that adhere to some conventions set by [boot-cljs][boot-cljs]
- `js-import` in [`cljsjs.app`][cljsjs-app] bundles files from jars created with the `cljsjs-jar` task to a single preamble file.

## Usage Example

A complete example for the `cljsjs-jar` task can be found in [cljsjs/react][cljsjs-react]'s `build.boot` file.

When you want to use one of the created jars there are tasks to help with that in your project:

An example:

```
FIXME/WIP
```

[boot]: https://github.com/boot-clj/boot
[cljsjs-packaging]: https://github.com/cljsjs/boot-cljsjs/blob/master/src/cljsjs/packaging.clj
[cljsjs-app]: https://github.com/cljsjs/boot-cljsjs/blob/master/src/cljsjs/app.clj
[boot-cljs]: https://github.com/adzerk/boot-cljs
[cljsjs-react]: https://github.com/cljsjs/packages/tree/master/react
