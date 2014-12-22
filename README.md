# boot-cljsjs

This project provides tasks for the [boot][boot] build system to package and
use Javascript dependencies in Clojurescript projects.

- [`cljsjs.app`][cljsjs-app] provides tasks to be used in application project
to add JS files from diffrent sources to fileset so they can be used by [boot-cljs][boot-cljs].
- [`cljsjs.packaging`][cljsjs-packaging] helps to create jars that adhere to some conventions set by [boot-cljs][boot-cljs]

## Using libraries

If you are using [boot-cljs][boot-cljs] you might want to use external JS libraries
with your project. Using boot-cljsjs you can add those external files from
jar files instead of copying them to your project.

Using boot-cljsjs you can use JS libraries from three sources in your project.

1. Cljsjs packaged jars
2. Webjars
3. Any jar-files containing js-files

For each source we have own task used to bring files from the source to the fileset.

1. `from-cljsjs`
  - This task will go through all jars in your classpath and find files in cljsjs prefix.
  - All files will be copied to filesset
2. `from-webjars`
  - This task will go through all jars in your classpath and find files in webjars prefix.
  - You need to provide name for file you want to import, e.g. "momentjs/moment.js"
    - This would import e.g. file "META-INF/resources/webjars/momentjs/2.8.3/moment.js" depending on version etc of the dependancy.
  - To import multiple files from Webjars, you would call this task multiple times.
3. `from-jars`
  - This task will just copy a file of given path from classpath. In effect this
  just calls `(io/resource path)` and copies found file to fileset.

### Why this works

[Boot-cljs][boot-cljs] will search fileset for files ending in `.inc.js`, `.ext.js` and `.lib.js`
and uses the found files for cljs compiler options.

In any case, on your html code you only need to include one source tag.
This works because in `:advanced` mode all files are concatenated into one.
In development mode you'll be using `:optimizations :none` and `:unified-mode true`
and in that case [boot-cljs][boot-cljs] will write a shim JS which will load
your all external libraries.

NOTE: In development mode the target file should by inside your docroot
so the files are accessible through your webserver.

### Example

NOTE: This a bad example because react is added to fileset twice.

```clj
(set-env! :dependencies '[[org.webjars/momentjs "2.8.3"]
                          [com.facebook/react "0.12.2"]

                          [cljsjs/boot-cljsjs "0.3.0-SNAPSHOT"]])

(require '[cljsjs.app :refer :all])

(deftask dev-deps []
  (set-env! :dependencies #(conj % '[cljsjs/react "0.12.1"]'))
  identity)

(deftask prod-deps []
  (set-env! :dependencies #(conj % '[cljsjs/react-min "0.12.1"]))
  identity)

(deftask dev []
  (comp
    (dev-deps)
    (from-cljsjs :target "public")
    (from-jars :path "react/react.js" :target "public/react.inc.js")
    (from-webjars :name "momentjs/moment.js" :target "public/moment.inc.js")
    (watch)))

(deftask package []
  (comp
    (prod-deps)
    (from-cljsjs :target "public")
    (from-jars :package true :path "react/min/react.min.js" :target "public/react.inc.js")
    (from-jars :package true :path "react/externs/react.js" :target "public/react.ext.js")
    (from-webjars :package true :name "momentjs/min/moment.min.js" :target "public/moment.inc.js")))
```

## Packaking jars

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
