(ns postgres.async
  (:require [postgres.async.impl :as pg]
            [clojure.core.async :refer [<!] :as async]
            postgres.async.cb)
  (:import [com.github.pgasync Db ConnectionPoolBuilder]
           [com.github.pgasync.impl.conversion DataConverter]))

(defmulti from-pg-value (fn [oid value] oid))
(defprotocol IPgParameter
  (to-pg-value [value]))

(defn- create-converter []
  (proxy [DataConverter] []
    (toConvertable [oid value]
      (from-pg-value oid value))
    (fromConvertable [value]
      (to-pg-value value))))

(defn open
  "Creates a db connection pool"
  [{:keys [hostname port username password database pool-size] :as config}]
  (doseq [param [:hostname :username :password :database]]
    (when (nil? (param config))
      (throw (IllegalArgumentException. (str param " is required")))))
  (-> (ConnectionPoolBuilder.)
      (.hostname hostname)
      (.port (or port 5432))
      (.database database)
      (.username username)
      (.password password)
      (.poolSize (or pool-size 25))
      (.dataConverter (create-converter))
      (.build)))

(defn close!
  "Closes a db connection pool"
  [^Db db]
  (.close db))

(defmacro defasync [name args]
  `(defn ~name [~@args]
     (let [ch# (async/chan 1)]
       (~(symbol (str "postgres.async.cb/" name)) ~@args (fn [rs# err#]
                                                           (async/put! ch# (or rs# err#))
                                                           (async/close! ch#)))
       ch#)))

(defasync execute!  [db query])
(defasync query!    [db query])
(defasync insert!   [db sql-spec data])
(defasync update!   [db sql-spec data])
(defasync begin!    [db])
(defasync commit!   [tx])
(defasync rollback! [tx])

(defmacro dosql
  "Takes values from channels returned by db functions and returns exception
   on first error. Returns the result of evaluating the given forms on success"
  [bindings & forms]
  (if-let [[l r & bindings] (not-empty bindings)]
    `(let [~l (<! ~r)]
       (if (instance? Throwable ~l)
         ~l
         (dosql ~bindings ~@forms)))
    `(do ~@forms)))
