# boot-cljsjs

Boot tasks to aid the creation of jars containing Javascript dependencies
in Clojurescript projects.

The main task is `cljsjs-jar` which creates a jar containing the specified
files and makes sure they conform to what's expected by [boot-cljs][boot-cljs].

## Example

    (require '[boot.core          :as  c]
             '[cljsjs.tasks       :as js])

    (js/cljsjs :project 'cljsjs/react
               :version "0.11.2"
               :inc-js ["react-0.11.2.js"]
               :ext-js ["react-externs.js"])

    (js/cljsjs :project 'cljsjs/react
               :version "0.11.2"
               :classifier :min
               :inc-js ["react-0.11.2.min.js"]
               :ext-js ["react-externs.js"])

A more complete example can be found in [cljsjs/react][cljsjs-react].

[boot-cljs]: https://github.com/adzerk/boot-cljs
[cljsjs-react]: https://github.com/cljsjs/react
