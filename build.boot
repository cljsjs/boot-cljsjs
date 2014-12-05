(set-env!
  :source-paths #{"src"}
  :dependencies '[[org.clojure/clojure       "1.6.0"       :scope "provided"]
                  [boot/core                 "2.0.0-pre27" :scope "provided"]
                  [tailrecursion/boot-useful "0.1.3"       :scope "test"]])

(require ;'[tailrecursion.boot-useful :refer :all]
 '[boot.core           :as  c]
 '[boot.util           :as  util]
 '[boot.git            :refer [last-commit]]
 '[boot.task.built-in  :as task]
 '[clojure.java.io     :as io])

(def +version+ "0.1.0")

(defn useful!
  [version]
  (set-env!
    :source-paths #{"src"}
    :repositories #(conj %
                     ["deploy-clojars"
                      {:url      "https://clojars.org/repo"
                       :username (System/getenv "CLOJARS_USER")
                       :password (System/getenv "CLOJARS_PASS")}]))

  (task-options! push [:repo           "deploy-clojars"
                       :ensure-branch  "master"
                       :ensure-clean   true
                       :ensure-version version
                       :ensure-tag     (last-commit)]))

(useful! +version+)

(deftask add-src []
  (with-pre-wrap fileset
    (-> (reduce
          add-resource
          fileset
          (input-dirs fileset))
        commit!)))

(deftask build-jar
  "Build jar and install to local repo."
  []
  (comp (pom) (add-src) (jar) (install)))

(task-options!
  pom  [:project     'cljsjs/boot-cljsjs
        :version     +version+
        :description "React.js packaged up with Google Closure externs"
        :url         "https://github.com/cljsjs/boot-cljsjs"
        :scm         {:url "https://github.com/cljsjs/boot-cljsjs"}
        :license     {:name "Eclipse Public License"
                      :url  "http://www.eclipse.org/legal/epl-v10.html"}])
