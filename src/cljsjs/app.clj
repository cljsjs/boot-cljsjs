(ns cljsjs.app
  {:boot/export-tasks true}
  (:require [boot.core          :as c]
            [boot.pod           :as pod]
            [boot.util          :as util]
            [boot.file          :as file]
            [boot.task.built-in :as task]
            [clojure.java.io    :as io]
            [clojure.string     :as string])
  (:import [java.net URL URI]
           [java.util UUID]))

(defn- jarfile-for
  [url]
  (-> url .getPath (.replaceAll "![^!]+$" "") URL. .toURI io/file))

(def dep-jars-on-cp
  (memoize
    (fn [env marker]
      (->> marker
        pod/resources
        (filter #(= "jar" (.getProtocol %)))
        (map jarfile-for)))))

(defn- in-dep-order
  [env jars]
  (let [jars-set (set jars)]
    (->> (pod/jars-in-dep-order env)
      (filter (partial contains? jars-set)))))

(def files-in-jar
  (memoize
    (fn [jarfile marker & [file-exts]]
      (->> jarfile
        pod/jar-entries
        (filter (fn [[p u]] (and (.startsWith p marker)
                              (or (empty? file-exts)
                                (some #(.endsWith p %) file-exts)))))))))

(defn cljs-dep-files
  [env exts]
  (->> "cljsjs/"
       (cljsjs.app/dep-jars-on-cp env)
       ;(in-dep-order env)
       (mapcat #(cljsjs.app/files-in-jar % "cljsjs/" exts))
       (map first)))
  ;; (->> "cljsjs/"
  ;;      (cljsjs.app/dep-jars-on-cp env)
  ;;      ;(in-dep-order env)
  ;;      (mapcat #(cljsjs.app/files-in-jar % "cljsjs/" exts)))

(c/deftask js-import
  "Seach jars specified as dependencies for files matching
   the following patterns and add them to the fileset:
    - cljsjs/**/*.inc.js
    - cljsjs/**/*.ext.js
    - cljsjs/**/*.lib.js"
  [c combined-preamble PREAMBLE str "Concat all .inc.js file into file at this destination"]
  (c/with-pre-wrap fileset
    (let [env  (c/get-env)
          inc  (-> env (cljs-dep-files [".inc.js"]))
          ext  (-> env (cljs-dep-files [".ext.js"]))
          lib  (-> env (cljs-dep-files [".lib.js"]))
          tmp  (c/temp-dir!)
          read #(slurp (io/resource %))]

      ; (require  'cljsjs.app :reload)
      ; (map first (->> "cljsjs/" (cljsjs.app/dep-jars-on-cp env) (mapcat #(cljsjs.app/files-in-jar % "cljsjs/" [".inc.js"]))))
      ; => ("cljsjs/react/react-0.11.2.inc.js")
      ; (boot (js-import :combined-preamble "core.js"))
      ; => Found 0 .inc.js files
      ; => Adding combined .inc.js files as core.js
      ; WHY?
      (util/info "Found %s .inc.js files\n" (count inc))
      (when combined-preamble
        (let [comb (io/file tmp combined-preamble)]
          (io/make-parents comb)
          (util/info "Adding combined .inc.js files as %s\n" combined-preamble)
          (spit comb (string/join "\n" (map read inc)))))
      (doseq [f (if combined-preamble
                  (concat ext lib)
                  (concat inc ext lib))]
        (util/info (str "Adding " f " to fileset\n"))
        (pod/copy-resource f (io/file tmp f)))
      (-> fileset (c/add-resource tmp) c/commit!))))

(defn- copy-file [tmp path target]
  (let [f (io/resource path)]
    (if f
      (pod/copy-url f (io/file tmp target))
      (throw (str "File " path " not found!")))))

(c/deftask from-jars
  "Add non-boot ready js files to the fileset"
  [p path PATH str "The path of file in classpath"
   t target TARGET str "Target path"
   x package bool "Don't include files in result"]
  (let [tmp (c/temp-dir!)
        state (atom nil)]
    (c/with-pre-wrap
      fileset
      (when-not @state (copy-file tmp path target))
      (-> fileset ((if package c/add-source c/add-resource) tmp) c/commit!))))

(def ^:private webjar-deps '[[org.webjars/webjars-locator "0.19"]
                             [org.slf4j/slf4j-nop "1.7.7"]])

(def ^:private webjar-pod (delay (pod/make-pod (update-in (c/get-env) [:dependencies] concat webjar-deps))))

(c/deftask from-webjars
  "Add file from webjars to fileset"
  [n name NAME str "webjar / asset path"
   t target TARGET str "Target path"
   x package bool "Don't include files in result"]
  (let [tmp (c/temp-dir!)
        state (atom nil)
        assets (pod/with-call-in @webjar-pod (cljsjs.impl.webjars/asset-map))]
    (c/with-pre-wrap
      fileset
      (when-not @state
        (copy-file tmp (get assets name) target))
      (-> fileset ((if package c/add-source c/add-resource) tmp) c/commit!))))
