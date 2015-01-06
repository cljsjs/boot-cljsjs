(ns cljsjs.boot-cljsjs.packaging
  {:boot/export-tasks true}
  (:require [boot.core          :as c]
            [boot.util          :as util]
            [clojure.java.io    :as io]
            [clojure.string     :as string])
  (:import [java.security DigestInputStream MessageDigest]
           [javax.xml.bind DatatypeConverter]
           [java.util.zip ZipFile]))

(c/deftask checksum
  [s sum FILENAME=CHECKSUM {str str} "Check the md5 checksum of file against md5"]
  (c/with-pre-wrap fileset
    (doseq [f (c/ls fileset)
            :let [path (c/tmppath f)]]
      (when-let [checksum (some-> (get sum path) string/upper-case)]
        (with-open [is (io/input-stream (c/tmpfile f))]
          (let [md (MessageDigest/getInstance "MD5")
                dis (DigestInputStream. is md)
                ; Read the whole file, we don't need output but to realize the digest
                _ (slurp dis)
                dig (.digest (.getMessageDigest dis))
                real-checksum (DatatypeConverter/printHexBinary dig)]
            (if (not= checksum real-checksum)
              (throw (IllegalStateException. (format "Checksum of file %s in not %s but %s" path checksum real-checksum))))))))
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
            (io/copy (.getInputStream zipfile entry) target)))
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
          (io/copy (io/input-stream url) target))
        (-> fileset (c/add-resource tmp) c/commit!))
      checksum (comp (cljsjs.boot-cljsjs.packaging/checksum :sum {fname checksum}))
      unzip    (comp (cljsjs.boot-cljsjs.packaging/unzip :paths #{fname})))))
