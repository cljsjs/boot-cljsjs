(set-env!
  :source-paths #{"src"}
  :dependencies '[[org.clojure/clojure       "1.6.0"       :scope "provided"]
                  [boot/core                 "2.0.0-pre28" :scope "provided"]
                  [adzerk/bootlaces          "0.1.5"       :scope "test"]])

(require
 '[boot.core           :as  c]
 '[boot.util           :as  util]
 '[boot.git            :refer [last-commit]]
 '[boot.task.built-in  :as task]
 '[adzerk.bootlaces    :refer :all]
 '[clojure.java.io     :as io]
 '[cljsjs.app :refer [js-import]])

(def +version+ "0.2.3-SNAPSHOT")

(bootlaces! +version+)

(task-options!
  pom  {:project     'cljsjs/boot-cljsjs
        :version     +version+
        :description "React.js packaged up with Google Closure externs"
        :url         "https://github.com/cljsjs/boot-cljsjs"
        :scm         {:url "https://github.com/cljsjs/boot-cljsjs"}
        :license     {:name "Eclipse Public License"
                      :url  "http://www.eclipse.org/legal/epl-v10.html"}})
