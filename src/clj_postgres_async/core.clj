(ns clj-postgres-async.core
  (:require [clj-postgres-async.impl.impl :refer :all])
  (:import [com.github.pgasync ConnectionPoolBuilder]
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

(defn open-db [{:keys [hostname port username password database pool-size]}]
  "Creates a db connection pool"
  (-> (ConnectionPoolBuilder.)
      (.hostname hostname)
      (.port (or port 5432))
      (.database database)
      (.username username)
      (.password password)
      (.poolSize (or pool-size 25))
      (.dataConverter (create-converter))
      (.build)))

(defn close-db! [db]
  "Closes a db connection pool"
  (.close db))

(defn execute! [db [sql & params] f]
  "Executes an sql statement and calls (f result-set exception) on completion"
  (.query db sql params
          (consumer-fn [rs]
                       (f (result->map rs (.getColumns rs)) nil))
          (consumer-fn [exception]
                       (f nil exception))))


(defn query! [db sql f]
  "Executes an sql query and calls (f rows exception) on completion"
  (execute! db sql (fn [rs err]
                     (f (:rows rs) err))))


(defn insert! [db spec data f]
  "Executes an sql insert and calls (f result-set exception) on completion"
  (execute! db (list* (create-insert-sql spec data)
                    (for [e data] (second e)))
          f))


(defn update! [db spec data f]
  "Executes an sql update and calls (f result-set exception) on completion"
  (execute! db (flatten [(create-update-sql spec data)
                        (rest (:where spec))
                        (for [e data] (second e))])
          f))

(defn begin! [db f]
  "Begins a transaction and calls (f transaction exception) on completion"
  (.begin db
          (consumer-fn [tx]
                       (f tx nil))
          (consumer-fn [exception]
                       (f nil exception))))

(defn commit! [tx f]
  "Commits an active transaction and calls (f true exception) on completion"
  (.commit tx
           #(f true nil)
           (consumer-fn [exception]
                        (f nil exception))))

(defn rollback! [tx f]
  "Rollbacks an active transaction and calls (f true exception) on completion"
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

(defmacro dosql [bindings & forms]
  "Takes values from channels returned by db functions and handles errors"
  (let [err (gensym "e")]
    `(let [~@(async-sql-bindings bindings err)]
       (if ~err
         [nil ~err]
         [(do ~@forms) nil]))))

