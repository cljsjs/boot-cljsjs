(ns cljsjs.packaging
  (:require [boot.core          :as  c]
            [boot.task.built-in :as task]
            [clojure.java.io    :as io]))

(defn- rename [f ext]
  (let [segs (vec (drop-last (clojure.string/split f #"\.")))
        append-ext #(clojure.string/join "." (conj segs %))]
    (case ext
      :inc (append-ext "inc.js")
      :ext (append-ext "ext.js")
      :lib (append-ext "lib.js"))))

(defn- input-file [name fs]
  (try
    (c/tmpfile
     (first
      (c/by-name [name] (c/input-files fs))))
    (catch Exception e
      (println (str "File " name " was not found in input files")))))

(def prefix "hoplon/include/")

(defn- copy-file [project fname type fs tmp-dir]
  (let [file   (input-file fname fs)
        target (str "cljsjs/" (name project) "/" (rename (.getName file) type))]
    (println (str "Copying " (.getName file) " to " target))
    (doto (io/file tmp-dir target)
      io/make-parents
      (spit (slurp file)))))

(c/deftask cljsjs-jar
  "Create a jar with the given classifier"
  [p project SYM sym  "The project id (eg. foo/bar)."
   v version V   str  "The project version (eg. 1.2.3)"
   c classifier  str  "Classifier used for generated artifact"
   i inc-js INC [str] "Files that should be included with the .inc.js extension"
   e ext-js EXT [str] "Files that should be included with the .ext.js extension"
   l lib-js LIB [str] "Files that should be included with the .lib.js extension"]
  (let [tmp (c/temp-dir!)
        v   (str version (if classifier (str "-" classifier)))]
    (comp
     (c/with-pre-wrap fileset
       (doseq [include inc-js]
         (copy-file project include :inc fileset tmp))
       (doseq [extern ext-js]
         (copy-file project extern :ext fileset tmp))
       (doseq [library lib-js]
         (copy-file project library :lib fileset tmp))
       (-> fileset
           (c/add-resource tmp)
           c/commit!))
     (task/pom :project project
               :version v)
     (task/jar))))
