(ns clj-postgres-async.core
  (:require [clojure.string :as string]
            [clojure.core.async :refer [chan put!] :as async])
  (:import [com.github.pgasync ConnectionPoolBuilder]
           [com.github.pgasync.callback ErrorHandler ResultHandler
            TransactionHandler TransactionCompletedHandler])
  (:gen-class))

(defn open-db [{:keys [hostname port username password database]}]
  (-> (ConnectionPoolBuilder.)
      (.hostname hostname)
      (.port port)
      (.database database)
      (.username username)
      (.password password)
      (.build)))

(defn close-db! [db]
  (.close db))

(defn- result->map [result]
  (let [columns (.getColumns result)]
    (map (fn [row]
           (reduce (fn [rowmap col]
                     (assoc rowmap (keyword (.toLowerCase col)) (.get row col)))
                   {}
                   columns))
         result)))

(defn query! [db [sql & params] f]
  (let [handler (reify
                  ResultHandler (onResult [_ r]
                                  (f [(result->map r) nil]))
                  ErrorHandler (onError [_ t]
                                 (f [nil t])))]
    (.query db sql params handler handler)))

(defn- create-insert-sql [table row]
  (str "INSERT INTO " (name table) " ("
       (string/join ", " (for [e row] (-> e (first) (name))))
       ") VALUES ("
       (string/join ", " (for [i (range 1 (inc (count row)))] (str "$" i)))
       ")"))

(defn insert! [db table row f]
  (query! db (list* (create-insert-sql table row)
                    (for [e row] (get e 1)))
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
     (let [c# (chan)]
       (~(symbol (subs (str name) 1)) ~@args #(put! c# %))
       c#)))

(defasync <query!    [db query])
(defasync <insert!   [db table row])
(defasync <begin!    [db])
(defasync <commit!   [tx])
(defasync <rollback! [tx])
