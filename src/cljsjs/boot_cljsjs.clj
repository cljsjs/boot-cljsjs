(ns cljsjs.boot-cljsjs
  {:boot/export-tasks true}
  (:require [boot.core          :as c]
            [boot.pod           :as pod]
            [boot.util          :as util]
            [clojure.java.io    :as io]
            [cljsjs.impl.jars   :as jars]))

(defn- get-classpath []
  (System/getProperty "boot.class.path"))

(defn- not-found [path]
  (throw (Throwable. (str "File " path " not found!"))))

(defn- copy-file [tmp path target]
  (let [f (io/resource path)]
    (if f
      (do
        (util/info (str "Adding " path " to fileset\n"))
        (pod/copy-url f (io/file tmp target)))
      (not-found path))))

(c/deftask from-cljsjs
  "Unpack cljsjs files from jar dependencies."
  [p profile ENV kw "Load production or development files"]
  (let [classpath  (atom nil)
        filemeta   (atom nil)
        tmp        (c/temp-dir!)
        profile    (or profile :development)]
    (c/with-pre-wrap fileset
      (when-not (= @classpath (get-classpath))
        (c/empty-dir! tmp)
        (reset! classpath (get-classpath))
        (let [env       (c/get-env)
              markers   ["cljsjs/common/" (str "cljsjs/" (name profile) "/")]
              files     (jars/cljs-dep-files env markers [])
              dep-order (-> (fn [xs [n p]]
                              (assoc xs p {:dependency-order n}))
                            (reduce {} (map-indexed list files)))]
          (prn :deps dep-order)
          (doseq [f files] (copy-file tmp f f))
          (reset! filemeta dep-order)))
      (-> fileset (c/add-source tmp) (c/add-meta @filemeta) c/commit!))))

(c/deftask from-jars
  "Add non-boot ready js files to the fileset"
  [p path PATH     str  "The path of file in classpath"
   t target TARGET str  "Target path"]
  (let [tmp (c/temp-dir!)
        classpath (atom nil)]
    (c/with-pre-wrap fileset
      (when-not (= @classpath (get-classpath))
        (reset! classpath (get-classpath))
        (copy-file tmp path target))
      (-> fileset (c/add-source tmp) c/commit!))))

(def ^:private webjar-deps '[[org.webjars/webjars-locator "0.19"]
                             [org.slf4j/slf4j-nop "1.7.7"]])

(def ^:private webjar-pod (delay (pod/make-pod (update-in (c/get-env) [:dependencies] concat webjar-deps))))

(c/deftask from-webjars
  "Add file from webjars to fileset"
  [n name NAME str "webjar / asset path"
   t target TARGET str "Target path"]
  (let [tmp (c/temp-dir!)
        classpath (atom nil)
        assets (pod/with-call-in @webjar-pod (cljsjs.impl.webjars/asset-map))]
    (c/with-pre-wrap fileset
      (when-not (= @classpath (get-classpath))
        (reset! classpath (get-classpath))
        (let [f (or (get assets name) (not-found name))]
          (copy-file tmp f target)))
      (-> fileset (c/add-source tmp) c/commit!))))

(c/deftask js-import
  "Task exists only for legacy support"
  [c combined-preamble PREAMBLE str "Concat all .inc.js file into file at this destination"]
  (comp
   (from-cljsjs)
   (c/with-pre-wrap fileset
     (let [inc-files  (c/by-ext [".inc.js"] (c/input-files fileset))
           tmp  (c/temp-dir!)]
       (util/info "Found %s .inc.js files\n" (count inc-files))
       (let [path (or combined-preamble "preamble.js")
             comb (io/file tmp path)]
         (util/info "Adding combined .inc.js files as %s\n" path)
         (io/make-parents comb)
         (spit comb "")
         (doseq [f inc-files]
           (spit comb (slurp (c/tmpfile f)) :append true)))
       (-> fileset (c/add-resource tmp) c/commit!)))))
