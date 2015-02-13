(ns postgres.async
  (:require [postgres.async.impl :refer [consumer-fn defasync] :as pg]
            [clojure.core.async :refer [<!]])
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

(defn close-db!
  "Closes a db connection pool"
  [^Db db]
  (.close db))

(defn execute!
  "Executes an sql statement and calls (f result-set exception) on completion"
  [^QueryExecutor db [sql & params] f]
  (.query db sql params
          (consumer-fn [rs]
                       (f (pg/result->map rs) nil))
          (consumer-fn [exception]
                       (f nil exception))))

(defn query!
  "Executes an sql query and calls (f rows exception) on completion"
  [db sql f]
  (execute! db sql (fn [rs err]
                     (f (:rows rs) err))))

(defn insert!
  "Executes an sql insert and calls (f result-set exception) on completion.
   Spec format is
     :table - table name
     :returning - sql string"
  [db sql-spec data f]
  (execute! db (list* (pg/create-insert-sql sql-spec data)
                      (if (map? data)
                        (vals data)
                        (flatten (map vals data))))
          f))

(defn update!
  "Executes an sql update and calls (f result-set exception) on completion.
   Spec format is
     :table - table name
     :returning - sql string
     :where - [sql & params]"
  [db sql-spec data f]
  (execute! db (flatten [(pg/create-update-sql sql-spec data)
                        (rest (:where sql-spec))
                        (vals data)])
          f))

(defn begin!
  "Begins a transaction and calls (f transaction exception) on completion"
  [^TransactionExecutor db f]
  (.begin db
          (consumer-fn [tx]
                       (f tx nil))
          (consumer-fn [exception]
                       (f nil exception))))

(defn commit!
  "Commits an active transaction and calls (f true exception) on completion"
  [^Transaction tx f]
  (.commit tx
           #(f true nil)
           (consumer-fn [exception]
                        (f nil exception))))

(defn rollback!
  "Rollbacks an active transaction and calls (f true exception) on completion"
  [^Transaction tx f]
  (.rollback tx
             #(f true nil)
             (consumer-fn [exception]
                          (f nil exception))))

(defasync <execute!  [db query])
(defasync <query!    [db query])
(defasync <insert!   [db sql-spec data])
(defasync <update!   [db sql-spec data])
(defasync <begin!    [db])
(defasync <commit!   [tx])
(defasync <rollback! [tx])

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
