(ns cljsjs.impl.download
  (:require [clojure.java.io     :as io]
            [clj-http.client     :as http]))

(defn download [url out-dir fname]
  (let [target (io/file out-dir fname)]
    (io/make-parents target)
    (with-open [is (:body (http/get url {:as :stream}))]
      (io/copy is target))))
