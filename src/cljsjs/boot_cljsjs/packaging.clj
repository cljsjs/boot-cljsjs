(ns cljsjs.boot-cljsjs.packaging
  {:boot/export-tasks true}
  (:require [boot.core           :as c]
            [boot.pod            :as pod]
            [boot.util           :as util]
            [boot.task.built-in  :as tasks]
            [clojure.java.io     :as io]
            [clojure.pprint      :as pprint]
            [clojure.string      :as string])
  (:import [java.security DigestInputStream MessageDigest]
           [javax.xml.bind DatatypeConverter]
           [java.util.zip ZipFile]))

(defn- realize-input-stream! [s]
  (loop [c (.read s)]
    (if-not (neg? c)
      (recur (.read s)))))

(defn- message-digest->str [^MessageDigest message-digest]
  (-> message-digest
      (.digest)
      (DatatypeConverter/printHexBinary)))

(c/deftask checksum
  [s sum FILENAME=CHECKSUM {str str} "Check the md5 checksum of file against md5"]
  (c/with-pre-wrap fileset
    (doseq [f (c/ls fileset)
            :let [path (c/tmp-path f)]]
      (when-let [checksum (some-> (get sum path) string/upper-case)]
        (with-open [is  (io/input-stream (c/tmp-file f))
                    dis (DigestInputStream. is (MessageDigest/getInstance "MD5"))]
          (realize-input-stream! dis)
          (let [real (message-digest->str (.getMessageDigest dis))]
            (if (not= checksum real)
              (throw (IllegalStateException. (format "Checksum of file %s in not %s but %s" path checksum real))))))))
    fileset))

(c/deftask unzip
  [p paths PATH #{str} "Paths in fileset to unzip"]
  (let [tmp (c/tmp-dir!)]
    (c/with-pre-wrap fileset
      (let [archives (filter (comp paths c/tmp-path) (c/ls fileset))]
        (doseq [archive archives
                :let [zipfile (ZipFile. (c/tmp-file archive))
                      entries (->> (.entries zipfile)
                                   enumeration-seq
                                   (remove #(.isDirectory %)))]]
          (util/info "Extracting %d files\n" (count entries))
          (doseq [entry entries
                  :let [target (io/file tmp (.getName entry))]]
            (io/make-parents target)
            (with-open [is (.getInputStream zipfile entry) ]
              (io/copy is target))))
        (-> fileset (c/rm archives) (c/add-resource tmp) c/commit!)))))

(def decompress-deps '[[org.apache.commons/commons-compress "1.14"]])

(c/deftask decompress
  [p paths PATH #{str} "Paths in fileset to untar"
   f compression-format FORMAT str "Compression format"
   F archive-format FORMAT str "Archive format"]
  (let [tmp (c/tmp-dir!)
        pod (future (pod/make-pod (-> (c/get-env) (update-in [:dependencies] into decompress-deps))))]
    (c/with-pre-wrap fileset
      (let [archives (filter (comp paths c/tmp-path) (c/ls fileset))]
        (doseq [archive archives]
          (pod/with-call-in @pod
            (cljsjs.impl.decompress/decompress-file ~(.getPath (c/tmp-file archive)) ~(.getPath tmp)
                                                    {:compression-format ~compression-format
                                                     :archive-format ~archive-format})))
        (-> fileset (c/rm archives) (c/add-resource tmp) c/commit!)))))

(def download-deps '[[clj-http "3.7.0"]])

(c/deftask download
  [u url      URL      str     "The url to download"
   n name     NAME     str     "Optional name for target file"
   c checksum CHECKSUM str     "Optional MD5 checksum of downloaded file"
   x unzip             bool    "Unzip the downloaded file"
   X decompress        bool    "Decompress the archive (tar, zip, gzip, bzip...)"
   f compression-format FORMAT str "Manually set format for decompression (e.g. lzma can't be autodetected)."
   F archive-format     FORMAT str "Manually set format for archive"
   t target   PATH     str     "Move the downloaded file to this path"]
  (let [tmp (c/tmp-dir!)
        pod (future (pod/make-pod (-> (c/get-env) (update-in [:dependencies] into download-deps))))
        fname (or name (last (string/split url #"/")))]
    (cond->
      (c/with-pre-wrap fileset
        (util/info "Downloading %s\n" fname)
        (pod/with-call-in @pod
          (cljsjs.impl.download/download ~url ~(.getPath tmp) ~fname))
        (-> fileset (c/add-resource tmp) c/commit!))
      checksum (comp (cljsjs.boot-cljsjs.packaging/checksum :sum {fname checksum}))
      unzip    (comp (cljsjs.boot-cljsjs.packaging/unzip :paths #{fname}))
      decompress (comp (cljsjs.boot-cljsjs.packaging/decompress :paths #{fname} :compression-format compression-format :archive-format archive-format))
      target (comp (tasks/sift :move {(re-pattern fname) target})))))

(c/deftask deps-cljs
  "Creates a deps.cljs file based on information in the fileset and
  what's passed as options.

  The first .inc.js file is passed as :file, similarily .min.inc.js
  is passed as :file-min. Files ending in .ext.js are passed as :externs.

  :requires can be specified through the :requires option.
  :provides is determined by what's passed to :name"
  [n name NAME str "Name for provided foreign lib"

   p provides PROV [str] "Modules provided by this lib"
   R requires REQ [str] "Modules required by this lib"
   g global-exports GLOBAL {sym sym} ""
   E no-externs bool "No externs are provided"]
  (let [tmp              (c/tmp-dir!)
        deps-file        (io/file tmp "deps.cljs")]
    (c/with-pre-wrap fileset
      (let [in-files (c/input-files fileset)
            regular  (first (c/by-ext [".inc.js"] (c/not-by-ext [".min.inc.js"] in-files)))
            minified (first (c/by-ext [".min.inc.js"] in-files))
            externs  (c/by-ext [".ext.js"] in-files)]

        (assert (or (seq provides) name) "Either list of provides or a name has to be provided.")
        (assert regular "No .inc.js file found!")

        (if-not no-externs
          (assert (first externs) "No .ext.js file(s) found!"))

        (util/info "Writing deps.cljs\n")

        (let [base-lib {:file (c/tmp-path regular)
                        :provides (or provides [name])}
              lib      (cond-> base-lib
                         requires (assoc :requires requires)
                         minified (assoc :file-min (c/tmp-path minified))
                         global-exports (assoc :global-exports global-exports))
              data     (merge {:foreign-libs [lib]}
                              (if (seq externs)
                                {:externs (mapv c/tmp-path externs)}))
              s (with-out-str (pprint/pprint data))]
          (println "deps.cljs:\n" s)
          (spit deps-file s)
          (-> fileset
              (c/add-resource tmp)
              c/commit!))))))

(defn minifier-pod []
  (pod/make-pod (assoc-in (c/get-env) [:dependencies] '[[asset-minifier "0.2.4"]])))

(c/deftask minify
  "Minifies .js and .css files based on their file extension

   NOTE: potentially slow when called with watch or multiple times"
  [i in  INPUT  str "Path to file to be compressed"
   o out OUTPUT str "Path to where compressed file should be saved"
   l lang LANGUAGE_IN kw "Language of the input javascript file. Default value is ecmascript3."]
  (assert in "Path to input file required")
  (assert out "Path to output file required")
  (let [tmp      (c/tmp-dir!)
        out-file (io/file tmp out)
        min-pod  (minifier-pod)]
    (c/with-pre-wrap fileset
      (let [in-files (c/input-files fileset)
            in-file  (c/tmp-file (first (c/by-re [(re-pattern in)] in-files)))
            in-path  (.getPath in-file)
            out-path (.getPath out-file)]
        (util/info "Minifying %s\n" (.getName in-file))
        (io/make-parents out-file)
        (cond
          (. in-path (endsWith "js"))
          (pod/with-eval-in min-pod
            (require 'asset-minifier.core)
            (asset-minifier.core/minify-js ~in-path ~out-path (if ~lang
                                                                {:language ~lang}
                                                                {})))
          (. in-path (endsWith "css"))
          (pod/with-eval-in min-pod
            (require 'asset-minifier.core)
            (asset-minifier.core/minify-css ~in-path ~out-path)))
        (-> fileset
            (c/add-resource tmp)
            c/commit!)))))

(c/deftask replace-content
  "Replaces portion of a file matching some pattern with some value."
  [i in INPUT str "Path to file to be modified"
   m match MATCH regex "Pattern to match"
   v value VALUE str "Value to replace with"
   o out OUTPUT str "Path to where modified file should be saved"]
  (assert in "Path to input file required")
  (let [tmp      (c/tmp-dir!)
        out-file (io/file tmp (or out in))]
    (c/with-pre-wrap fileset
      (let [in-files (c/input-files fileset)
            in-file  (c/tmp-file (first (c/by-re [(re-pattern in)] in-files)))
            in-path  (.getPath in-file)]
        (util/info "Replacing content of %s\n" (.getName in-file))
        (io/make-parents out-file)
        (spit out-file (string/replace (slurp in-file) match value))
        (-> fileset
            (c/add-resource tmp)
            c/commit!)))))
