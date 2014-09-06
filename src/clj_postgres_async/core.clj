(ns clj-postgres-async.core
  (:require [clojure.string :as string]
            [clojure.core.async :refer [chan put! go <!] :as async])
  (:import [com.github.pgasync ConnectionPoolBuilder]
           [com.github.pgasync.callback ErrorHandler ResultHandler
            TransactionHandler TransactionCompletedHandler])
  (:gen-class))

(defn open-db [{:keys [hostname port username password database pool-size]}]
  (-> (ConnectionPoolBuilder.)
      (.hostname hostname)
      (.port (or port 5432))
      (.database database)
      (.username username)
      (.password password)
      (.poolSize (or pool-size 25))
      (.build)))

(defn close-db! [db]
  (.close db))

(defn- result->map [result]
  (let [columns (.getColumns result)
        rows    (map (fn [row]
                       (reduce (fn [rowmap col]
                                 (assoc rowmap
                                   (keyword (.toLowerCase col))
                                   (.get row col)))
                               {}
                               columns))
                     result)]
    {:updated (.updatedRows result) :rows (into [] rows)}))

(defn execute! [db [sql & params] f]
  (let [handler (reify
                  ResultHandler (onResult [_ r]
                                  (f [(result->map r) nil]))
                  ErrorHandler (onError [_ t]
                                 (f [nil t])))]
    (.query db sql params handler handler)))

(defn query! [db sql f]
  (execute! db sql (fn [[rs err]]
                     (f [(:rows rs) err]))))

(defn- create-insert-sql [spec data]
  (let [cols   (for [e data] (-> e (first) (name)))
        params (for [i (range 1 (inc (count data)))] (str "$" i))]
    (str "INSERT INTO " (:table spec)
         " (" (string/join ", " cols) ") "
         "VALUES (" (string/join ", " params) ")"
         (if-let [ret (:returning spec)]
           (str " RETURNING " ret)))))

(defn insert! [db spec data f]
  (execute! db (list* (create-insert-sql spec data)
                    (for [e data] (second e)))
          f))

(defn- create-update-sql [spec data]
  (let [where (:where spec)
        cols   (for [e data] (-> e (first) (name)))
        params (for [i (range (count where) (+ (count where) (count data)))]
                 (str "$" i))]
    (str "UPDATE " (:table spec)
         " SET (" (string/join "," cols)
         ")=(" (string/join "," params) ")"
         " WHERE " (first where)
         (if-let [ret (:returning spec)]
           (str " RETURNING " ret)))))

(defn update! [db spec data f]
  (execute! db (flatten [(create-update-sql spec data)
                        (rest (:where spec))
                        (for [e data] (second e))])
          f))

(defn begin! [db f]
  (let [handler (reify
                  TransactionHandler (onBegin [_ tx]
                                       (f [tx nil]))
                  ErrorHandler (onError [_ t]
                                 (f [nil t])))]
    (.begin db handler handler)))

(defn- complete-tx [tx completion-fn f]
  (let [handler (reify
                  TransactionCompletedHandler (onComplete [_]
                                       (f [true nil]))
                  ErrorHandler (onError [_ t]
                                 (f [nil t])))]
    (completion-fn handler)))

(defn commit! [tx f]
  (complete-tx tx #(.commit tx %1 %1) f))

(defn rollback! [tx f]
  (complete-tx tx #(.rollback tx %1 %1) f))

(defmacro defasync [name args]
  `(defn ~name [~@args]
     (let [c# (chan 1)]
       (~(symbol (subs (str name) 1)) ~@args #(put! c# %))
       c#)))

(defasync <execute!  [db query])
(defasync <query!    [db query])
(defasync <insert!   [db table data])
(defasync <update!   [db sql-spec data])
(defasync <begin!    [db])
(defasync <commit!   [tx])
(defasync <rollback! [tx])

(defn- async-sql-bindings [bindings err]
  "Converts bindings x (f) to [x err] (if [err] [nil err] (<! (f)))"
  (let [vars (map (fn [v]
                    [v err])
                  (take-nth 2 bindings))
        fs   (map (fn [f]
                    `(if ~err [nil ~err] (<! ~f)))
                  (take-nth 2 (rest bindings)))]
    (list* [err err] [nil nil] (interleave vars fs))))

(defmacro dosql [bindings & forms]
  "Takes values from channels returned by db functions and handles errors"
  (let [err (gensym "e")]
    `(let [~@(async-sql-bindings bindings err)]
       (if ~err
         [nil ~err]
         [(do ~@forms) nil]))))

