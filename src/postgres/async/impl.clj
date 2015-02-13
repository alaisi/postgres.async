(ns postgres.async.impl
  (:require [clojure.string :as string]
            [clojure.core.async :refer [chan close! put! go]])
  (:import [java.util.function Consumer]
           [com.github.pgasync ResultSet]
           [com.github.pgasync.impl PgRow]))

(defmacro defasync [name args]
  `(defn ~name [~@args]
     (let [c# (chan 1)]
       (~(symbol (subs (str name) 1)) ~@args #(do
                                                (put! c# (or %2 %1))
                                                (close! c#)))
       c#)))

(defmacro consumer-fn [[param] body]
  `(reify Consumer (accept [_# ~param]
                     (~@body))))

(defn column->value [^PgRow row ^String col]
  (let [r (.get row col)]
    (if (-> r .getClass .isArray)
      (vec r)
      r)))

(defn result->map [^ResultSet result]
  (let [columns (.getColumns result)
        row->map (fn [^PgRow row rowmap ^String col]
            (assoc rowmap (keyword (.toLowerCase col)) (column->value row col)))]
    {:updated (.updatedRows result)
     :rows (vec (map (fn [row]
                           (reduce (partial row->map row) {} columns))
                         result))}))

(defn- list-columns [data]
  (if (map? data)
    (str " (" (string/join ", " (map name (keys data))) ") ")
    (recur (first data))))

(defn- list-params [start end]
  (str "(" (string/join ", " (map (partial str "$")
                                  (range start end))) ")"))

(defn- list-params-seq [data]
  (if (map? data)
    (list-params 1 (inc (count data)))
    (let [size   (count (first data))
          max    (inc (* (count data) size))
          params (map (partial str "$") (range 1 max))]
      (string/join ", " (map
                         #(str "(" (string/join ", " %) ")")
                         (partition size params))))))

(defn create-insert-sql [{:keys [table returning]} data]
  (str "INSERT INTO " table
       (list-columns data)
       " VALUES "
       (list-params-seq data)
       (when returning
         (str " RETURNING " returning))))

(defn create-update-sql [{:keys [table returning where]} data]
  (str "UPDATE " table
       " SET "
       (list-columns data)
       " = "
       (list-params (count where) (+ (count where) (count data)))
       " WHERE " (first where)
       (when returning
         (str " RETURNING " returning))))
