(ns cljsjs.impl.decompress
  (:require [boot.util           :as util]
            [clojure.java.io     :as io])
  (:import [org.apache.commons.compress.compressors CompressorStreamFactory FileNameUtil ]
           [org.apache.commons.compress.archivers ArchiveStreamFactory ArchiveInputStream]))

(def file-name-util (delay (FileNameUtil. {".gz" ""
                                           ".xz" ""
                                           ".bzip2" ""
                                           ".bz2" ""
                                           ".tgz" ".tar"
                                           ".tbz2" ".tar"
                                           ".tlz" ".tar"}
                                          "")))

(defn not-decompressable? [e]
  (re-find #"No Compressor found for the stream signature\." (.getMessage e)))

(defn try-decompress-stream [is & [{:keys [format]}]]
  (try
    (cond-> (CompressorStreamFactory.)
      format       (.createCompressorInputStream format is true) ;; for concatenated bzip2, gzip and xz
      (not format) (.createCompressorInputStream is))
    (catch Exception e
      (if (not-decompressable? e)
        is
        (throw e)))))

(defn mark-not-supported? [e]
  (re-find #"Mark is not supported\." (.getMessage e)))

(defn unpackage-stream [is & [{:keys [format]}]]
  (try
    (cond-> (ArchiveStreamFactory.)
      format       (.createArchiveInputStream format is)
      (not format) (.createArchiveInputStream is))
    (catch Exception e
      (if (mark-not-supported? e)
        is
        (throw e)))))

(defn decompress-file [in out-dir & [{:keys [compression-format archive-format]}]]
  (with-open [is (-> (io/input-stream in)
                     (try-decompress-stream {:format compression-format})
                     (unpackage-stream {:format archive-format}))]
    (if (instance? ArchiveInputStream is)
      (let [count (loop [i 0
                         entry (.getNextEntry is)]
                    (if entry
                      (if-not (.isDirectory entry)
                        (let [target (io/file out-dir (.getName entry))]
                          (io/make-parents target)
                          ;; After .getNextEntry the stream points to the specific archive entry
                          (io/copy is target)
                          (recur (inc i) (.getNextEntry is)))
                        (recur i (.getNextEntry is)))
                      i))]
        (util/info (format "Extracted %d files\n" count)))
      (let [target (io/file out-dir (.getUncompressedFilename @file-name-util (.getName (io/file in))))]
        (io/make-parents target)
        (io/copy is target)
        (util/info (format "Extracted 1 file\n"))))))
