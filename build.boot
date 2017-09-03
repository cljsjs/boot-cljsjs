(set-env!
  :resource-paths #{"src"}
  :dependencies '[[org.apache.commons/commons-compress "1.14" :scope "test"]
                  [clj-http "3.7.0" :scope "test"]
                  [asset-minifier "0.2.4" :scope "test"]])

(def +version+ "0.7.0")

(task-options!
  pom  {:project     'cljsjs/boot-cljsjs
        :version     +version+
        :description "Tooling to package and deploy Javascript libraries for Clojurescript projects"
        :url         "https://github.com/cljsjs/boot-cljsjs"
        :scm         {:url "https://github.com/cljsjs/boot-cljsjs"}
        :license     {"EPL" "http://www.eclipse.org/legal/epl-v10.html"}})

(deftask build []
  (comp
   (pom)
   (jar)
   (install)))

(deftask dev []
  (comp
    (watch)
    (build)
    (repl :server true)))

(deftask deploy []
  (comp
   (build)
   (push :repo "clojars" :gpg-sign (not (.endsWith +version+ "-SNAPSHOT")))))
