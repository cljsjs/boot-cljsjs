(ns cljsjs.impl.jars
  (:require [boot.core          :as c]
            [boot.pod           :as pod]
            [clojure.java.io    :as io])
  (:import [java.net URL URI]))

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
  [env markers exts]
  (letfn [(files [marker] (->> marker
                               (dep-jars-on-cp env)
                               ; FIXME this breaks stuff currently
                               ;(in-dep-order env)
                               (mapcat #(files-in-jar % marker exts))
                               (map first)))]
    (apply concat (map files markers))))
