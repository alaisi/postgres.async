(ns postgres.async.json
  (:require [postgres.async :refer [from-pg-value IPgParameter]]
            [cheshire.core :as json]
            [cheshire.parse])
  (:import [java.nio.charset StandardCharsets]))

(defn- json->coll [^bytes value]
  (binding [cheshire.parse/*use-bigdecimals?* true]
    (json/parse-string (String. value StandardCharsets/UTF_8) true)))

(defn- coll->json [coll]
  (.getBytes (json/generate-string coll) StandardCharsets/UTF_8))

(extend-protocol IPgParameter 
  clojure.lang.IPersistentCollection
  (to-pg-value [coll]
    (coll->json coll))
  clojure.lang.IPersistentMap
  (to-pg-value [map]
    (coll->json map)))

(defmethod from-pg-value com.github.pgasync.impl.Oid/JSON [oid value]
  (json->coll value))

(defmethod from-pg-value com.github.pgasync.impl.Oid/JSONB [oid value]
  (json->coll value))

