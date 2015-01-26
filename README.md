# boot-cljsjs

<img src="https://dl.dropboxusercontent.com/u/453692/cljsjs-logo.png"
  alt="CLJSJS logo" align="right" />

[](dependency)
```clojure
[cljsjs/boot-cljsjs "0.4.2"] ;; latest release
```
[](/dependency)

This project provides tasks for the [boot][boot] build system to
package Javascript dependencies in Clojurescript projects.

- [cljsjs.boot-cljsjs.packaging][packaging-ns] provides tasks to help
  packaging of libraries for cljsjs.

## Using libraries

Please refer to the [packages][cljsjs-packages] project for documentation.

## Packaging a library

There are various tasks in the `cljsjs.boot-cljsjs.packaging` namespace to smooth
packaging of libraries:

- `download` can be used to download zip files containing the files you want to package
- `sift`, which is a regular Boot task, can be used to move files to the desired locations and filter out files you don't want to end up in the jar
- `deps-cljs` creates a `deps.cljs` file based on the information in the fileset

**Full example:**

```clojure
(deftask package []
  (comp
    (download :url "https://github.com/facebook/react/releases/download/v0.12.2/react-0.12.2.zip"
              :checksum "6a242238790b21729a88c26145eca6b9"
              :unzip true)
    (sift :move {#"^react-.*/build/react.js" "cljsjs/development/react.inc.js"
                 #"^react-.*/build/react.min.js" "cljsjs/production/react.min.inc.js"})
    (sift :include #{#"^cljsjs"})
    (deps-cljs :name "cljsjs.react")))

;; This would then be used like this:
;; boot package build-jar
```

# License

Copyright © 2014 Martin Klepsch and Juho Teperi

Distributed under the Eclipse Public License, the same as Clojure.

[boot]: https://github.com/boot-clj/boot
[cljsjs-packages]: https://github.com/cljsjs/packages
[packaging-ns]: src/cljsjs/boot_cljsjs/packaging.clj
[main-ns]: src/cljsjs/boot_cljsjs.clj
[boot-cljs]: https://github.com/adzerk/boot-cljs
[cljsjs-react]: https://github.com/cljsjs/packages/tree/master/react
