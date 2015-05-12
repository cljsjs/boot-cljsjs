(ns cljsjs.impl.decompress
  (:require [boot.util           :as util]
            [clojure.java.io     :as io])
  (:import [org.apache.commons.compress.compressors CompressorStreamFactory]
           [org.apache.commons.compress.archivers ArchiveStreamFactory]))

(defn not-decompressable? [e]
  (re-find #"No Compressor found for the stream signature\." (.getMessage e)))

(defn try-decompress-stream [is & [{:keys [format]}]]
  (try
    (cond-> (CompressorStreamFactory.)
      format       (.createCompressorInputStream format is)
      (not format) (.createCompressorInputStream is))
    (catch Exception e
      (if (not-decompressable? e)
        is
        (throw e)))))

(defn unpackage-stream [is & [{:keys [format]}]]
  (cond-> (ArchiveStreamFactory.)
    format       (.createArchiveInputStream format is)
    (not format) (.createArchiveInputStream is)))

(defn decompress-file [in out-dir & [{:keys [compression-format archive-format]}]]
  (with-open [is (-> (io/input-stream in)
                     (try-decompress-stream {:format compression-format})
                     (unpackage-stream {:format archive-format}))]
    (let [count (loop [i 0
                       entry (.getNextEntry is)]
                  (if entry
                    (if-not (.isDirectory entry)
                      (let [target (io/file out-dir (.getName entry))]
                        (io/make-parents target)
                        ; After .getNextEntry the stream points to the specific archive entry
                        (io/copy is target)
                        (recur (inc i) (.getNextEntry is)))
                      (recur i (.getNextEntry is)))
                    i))]
      (util/info (format "Extracted %d files\n" count)))))
