(ns cljsjs.boot-cljsjs.packaging
  {:boot/export-tasks true}
  (:require [boot.core           :as c]
            [boot.util           :as util]
            [clojure.java.io     :as io]
            [clojure.pprint      :as pprint]
            [clojure.string      :as string]
            [asset-minifier.core :as min])
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
            :let [path (c/tmppath f)]]
      (when-let [checksum (some-> (get sum path) string/upper-case)]
        (with-open [is  (io/input-stream (c/tmpfile f))
                    dis (DigestInputStream. is (MessageDigest/getInstance "MD5"))]
          (realize-input-stream! dis)
          (let [real (message-digest->str (.getMessageDigest dis))]
            (if (not= checksum real)
              (throw (IllegalStateException. (format "Checksum of file %s in not %s but %s" path checksum real))))))))
    fileset))

(c/deftask unzip
  [p paths PATH #{str} "Paths in fileset to unzip"]
  (let [tmp (c/temp-dir!)]
    (c/with-pre-wrap fileset
      (let [archives (filter (comp paths c/tmppath) (c/ls fileset))]
        (doseq [archive archives
                :let [zipfile (ZipFile. (c/tmpfile archive))
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

(c/deftask download
  [u url      URL      str     "The url to download"
   n name     NAME     str     "Optional name for target file"
   c checksum CHECKSUM str     "Optional MD5 checksum of downloaded file"
   x unzip             bool    "Unzip the downloaded file"]
  (let [tmp (c/temp-dir!)
        fname (or name (last (string/split url #"/")))]
    (cond->
      (c/with-pre-wrap fileset
        (let [target (io/file tmp fname)]
          (util/info "Downloading %s\n" fname)
          (with-open [is (io/input-stream url) ]
            (io/copy is target)))
        (-> fileset (c/add-resource tmp) c/commit!))
      checksum (comp (cljsjs.boot-cljsjs.packaging/checksum :sum {fname checksum}))
      unzip    (comp (cljsjs.boot-cljsjs.packaging/unzip :paths #{fname})))))

(c/deftask deps-cljs
  "Creates a deps.cljs file based on information in the fileset and
  what's passed as options.

  The first .inc.js file is passed as :file, similarily .min.inc.js
  is passed as :file-min. Files ending in .ext.js are passed as :externs.

  :requires can be specified through the :requires option.
  :provides is determined by what's passed to :name"
  [n name NAME str "Name for provided foreign lib"
   R requires REQ [str] "Modules required by this lib"]
  (let [tmp              (c/temp-dir!)
        deps-file        (io/file tmp "deps.cljs")
        write-deps-cljs! #(spit deps-file (pr-str %))]
    (c/with-pre-wrap fileset
      (let [in-files (c/input-files fileset)
            regular  (c/tmppath (first (c/by-ext [".inc.js"]
                                                 (c/not-by-ext [".min.inc.hs"] in-files))))
            minified (c/tmppath (first (c/by-ext [".min.inc.js"] in-files)))
            externs  (mapv c/tmppath (c/by-ext [".ext.js"] in-files))
            base-lib {:file regular
                      :file-min minified
                      :provides [name]}
            lib      (if requires
                        (merge base-lib {:requires requires})
                        base-lib)]
        (util/info "Writing deps.cljs\n")
        (write-deps-cljs! {:foreign-libs [lib]
                           :externs externs})
        (-> fileset
            (c/add-resource tmp)
            c/commit!)))))

(c/deftask minify
  ""
  [i in  INPUT  str "Path to file to be compressed"
   o out OUTPUT str "Path to where compressed file should be saved"]
  (assert in "Path to input file required")
  (assert out "Path to output file required")
  (let [tmp        (c/temp-dir!)
        out-file   (io/file tmp out)]
    (c/with-pre-wrap fileset
      (let [in-files (c/input-files fileset)
            in-file  (c/tmpfile (first (c/by-re [(re-pattern in)] in-files)))]
        (util/info "Minifying %s\n" (.getPath in-file))
        (min/minify-js in-file out-file)
        (-> fileset
            (c/add-resource tmp)
            c/commit!)))))
