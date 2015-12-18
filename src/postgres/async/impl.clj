(ns postgres.async.impl
  (:require [clojure.string :as string]
            [clojure.core.async :refer [chan close! put! go]])
  (:import [java.util.function Consumer]
           [com.github.pgasync ResultSet]
           [com.github.pgasync.impl PgRow]))

(defmacro consumer-fn [[param] body]
  `(reify Consumer (accept [_# ~param]
                     (~@body))))

(defn result-chan [f & args]
  (let [channel  (chan 1)
        callback (fn [rs err]
                   (put! channel (or err rs))
                   (close! channel))]
    (apply f (concat args [callback]))
    channel))

(defn- column->value [^Object value]
  (if (and value (-> value .getClass .isArray))
    (vec (map column->value value))
    value))

;; TODO: make columns public in the Java driver
(defn- get-columns [^PgRow row]
  (-> (doto (.getDeclaredField PgRow "columns")
        (.setAccessible true))
      (.get row)
      (keys)))

(defn- row->map [^PgRow row ^Object rowmap ^String col]
  (assoc rowmap
         (keyword (.toLowerCase col))
         (column->value (.get row col))))

(defn result->map [^ResultSet result]
  (let [columns (.getColumns result)]
    {:updated (.updatedRows result)
     :rows (vec (map (fn [row]
                       (reduce (partial row->map row) {} columns))
                     result))}))

(defn ^rx.Observer row-observer [channel]
  (reify rx.Observer
    (onNext [_ row]
      (put! channel (reduce (partial row->map row)
                            {} (get-columns row))))
    (onError [_ err]
      (put! channel err)
      (close! channel))
    (onCompleted [_]
      (close! channel))))

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


