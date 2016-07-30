(set-env!
  :resource-paths #{"src"})

(def +version+ "0.6.0-SNAPSHOT")

(task-options!
  pom  {:project     'cljsjs/boot-cljsjs
        :version     +version+
        :description "Tooling to package and deploy Javascript
                      libraries for Clojurescript projects"
        :url         "https://github.com/cljsjs/boot-cljsjs"
        :scm         {:url "https://github.com/cljsjs/boot-cljsjs"}
        :license     {"EPL" "http://www.eclipse.org/legal/epl-v10.html"}})

(deftask build []
  (comp
   (pom)
   (jar)
   (install)))

(deftask deploy []
  (comp
   (build)
   (push :repo "clojars" :gpg-sign (not (.endsWith +version+ "-SNAPSHOT")))))
