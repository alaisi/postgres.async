(ns postgres.async
  (:require [postgres.async.impl :refer [consumer-fn result-chan] :as pg]
            [clojure.core.async :refer [<! chan put! close!]])
  (:import [com.github.pgasync Db ConnectionPoolBuilder
            QueryExecutor TransactionExecutor Transaction]
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

(defn open-db
  "Creates a db connection pool"
  [{:keys [hostname port username password database
           pool-size ssl validation-query pipeline]
    :as config}]
  (doseq [param [:hostname :username :password :database]]
    (when (nil? (param config))
      (throw (IllegalArgumentException. (str param " is required")))))
  (-> (ConnectionPoolBuilder.)
      (.hostname hostname)
      (.port (or port 5432))
      (.database database)
      (.username username)
      (.password password)
      (.ssl (boolean ssl))
      (.pipeline (boolean pipeline))
      (.poolSize (or pool-size 25))
      (.validationQuery (or validation-query ""))
      (.dataConverter (create-converter))
      (.build)))

(defn close-db!
  "Closes a db connection pool"
  [^Db db]
  (.close db))

(defn execute!
  "Executes an sql statement with parameters and returns result rows and update count."
  ([db sql]
     (result-chan execute! db sql))
  ([^QueryExecutor db [sql & params] f]
     (.query db sql params
             (consumer-fn [rs]
                          (f (pg/result->map rs) nil))
             (consumer-fn [exception]
                          (f nil exception)))))

(defn query!
  "Executes an sql query with parameters and returns result rows."
  ([db sql]
     (result-chan query! db sql))
  ([db sql f]
     (execute! db sql (fn [rs err]
                        (f (:rows rs) err)))))

(defn query-rows!
  "Executes an sql query with parameters and returns a channel where 0-n rows are emitted."
  [^QueryExecutor db [sql & params]]
  (let [c (chan)]
    (-> (.queryRows db sql (into-array params))
        (.subscribe (pg/row-observer c)))
    c))

(defn insert!
  "Executes an sql insert and returns update count and returned rows.
   Spec format is
     :table - table name
     :returning - sql string"
  ([db sql-spec data]
     (result-chan insert! db sql-spec data))
  ([db sql-spec data f]
     (execute! db (list* (pg/create-insert-sql sql-spec data)
                         (if (map? data)
                           (vals data)
                           (flatten (map vals data))))
               f)))

(defn update!
  "Executes an sql update and returns update count and returned rows.
   Spec format is
     :table - table name
     :returning - sql string
     :where - [sql & params]"
  ([db sql-spec data]
     (result-chan update! db sql-spec data))
  ([db sql-spec data f]
     (execute! db (concat [(pg/create-update-sql sql-spec data)]
                          (rest (:where sql-spec))
                          (vals data))
               f)))

(defn begin!
  "Begins a transaction."
  ([db]
     (result-chan begin! db))
  ([^TransactionExecutor db f]
     (.begin db
             (consumer-fn [tx]
                          (f tx nil))
             (consumer-fn [exception]
                          (f nil exception)))))

(defn commit!
  "Commits an active transaction."
  ([tx]
     (result-chan commit! tx))
  ([^Transaction tx f]
     (.commit tx
              #(f true nil)
              (consumer-fn [exception]
                           (f nil exception)))))

(defn rollback!
  "Rollbacks an active transaction."
  ([tx]
     (result-chan rollback! tx))
  ([^Transaction tx f]
     (.rollback tx
                #(f true nil)
                (consumer-fn [exception]
                             (f nil exception)))))

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
