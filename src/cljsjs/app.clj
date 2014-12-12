(ns cljsjs.app
  (:require [boot.core          :as  c]
            [boot.pod           :as pod]
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

(defn- dep-files
  [env marker & [file-exts]]
  (->> marker
    (dep-jars-on-cp env)
    (in-dep-order env)
    (mapcat #(files-in-jar % marker file-exts))))

(defn- cljs-dep-files
  [env]
  (let [marker "cljsjs/"
        exts   [".inc.js" ".lib.js" ".ext.js"]]
    (dep-files env marker exts)))

(c/deftask js-import
  "Seach jars specified as dependencies for files matching
   the following patterns and add them to the fileset:
    - cljsjs/*.inc.js
    - cljsjs/*.ext.js
    - cljsjs/*.lib.js"
  []
  (c/with-pre-wrap fileset
    (let [from-jars (->> (c/get-env ) cljs-dep-files (map first))
          in-files  (c/input-files fileset)
          tmp       (c/temp-dir!)]
      (doseq [f from-jars]
        (println "Adding" f "to fileset")
        (pod/copy-resource f (io/file tmp f)))
      (-> fileset
         (c/add-resource tmp)
          c/commit!))))

(c/deftask preamble
  "Seach fileset for .inc.js files and concat to file at :output-to"
  [o output-to PATH str "Path where combined inc.js files should be saved"]
  (c/with-pre-wrap fileset
    (let [inc-js (c/by-ext [".inc.js"] (c/input-files fileset))
          tmp    (c/temp-dir!)
          out    (io/file tmp output-to)
          read   #(slurp (c/tmpfile %))]
      (io/make-parents out)
      (spit
       out
       (string/join "\n" (map read inc-js)))
      (-> fileset
         (c/add-resource tmp)
          c/commit!))))
