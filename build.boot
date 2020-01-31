(set-env!
  :resource-paths #{"src"}
  ;; Note: these deps are just for testing locally, running repl etc.
  ;; All Boot task also declare their depdendencies in the cljsjs.boot-cljsjs.packaging
  ;; namespace, so remember to update those also!
  :dependencies '[[org.apache.commons/commons-compress "1.14" :scope "test"]
                  [clj-http "3.7.0" :scope "test"]
                  [org.clojure/clojurescript "1.10.238" :scope "test"]
                  [cljsjs/react-dom "16.1.0-0" :scope "test"]
                  ;; Conflicts with cljs
                  #_[asset-minifier "0.2.6" :scope "test" :exclusions []]])

(def +version+ "0.10.5")

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
