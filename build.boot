(set-env!
  :source-paths #{"src"}
  :dependencies '[[adzerk/bootlaces          "0.1.8"       :scope "test"]])

(require '[adzerk.bootlaces :refer :all])

(def +version+ "0.3.0-SNAPSHOT")

(bootlaces! +version+)

(task-options!
  pom  {:project     'cljsjs/boot-cljsjs
        :version     +version+
        :description "Tooling to package and deploy Javascript
                      libraries for Clojurescript projects"
        :url         "https://github.com/cljsjs/boot-cljsjs"
        :scm         {:url "https://github.com/cljsjs/boot-cljsjs"}
        :license     {:name "Eclipse Public License"
                      :url  "http://www.eclipse.org/legal/epl-v10.html"}})
