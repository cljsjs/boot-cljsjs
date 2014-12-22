(ns cljsjs.impl.webjars
  (:import [org.webjars WebJarAssetLocator]))

; Source: https://github.com/weavejester/ring-webjars/blob/master/src/ring/middleware/webjars.clj

(def ^:private webjars-pattern
  #"META-INF/resources/webjars/([^/]+)/([^/]+)/(.*)")

(defn- asset-path [resource]
  (let [[_ name version path] (re-matches webjars-pattern resource)]
    (str name "/" path)))

(def ^:private locator (delay (WebJarAssetLocator.)))

(defn- asset-map []
  (->> (.listAssets @locator "")
       (map (juxt asset-path identity))
       (into {})))
