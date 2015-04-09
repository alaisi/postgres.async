(ns postgres.async.cb
  (:require [postgres.async.impl :refer [consumer-fn] :as pg])
  (:import [com.github.pgasync QueryExecutor TransactionExecutor Transaction]))

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
