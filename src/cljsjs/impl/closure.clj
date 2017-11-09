(ns cljsjs.impl.closure
  (:require [cljs.closure :as closure]
            [boot.util :as util]
            [clojure.pprint :as pprint]))

(defn validate-externs! []
  (let [deps (closure/get-upstream-deps*)]
    (util/info (str "\nFound libs and externs:\n" (with-out-str (pprint/pprint deps)) "\n"))
    (closure/build '[(def ^:export hello "OK")]
                   {:optimizations :advanced
                    :closure-warnings {:check-types :warning}})))

(comment
  (validate-externs!))
