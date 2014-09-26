(ns postgres.async.impl
  (:require [clojure.string :as string]
            [clojure.core.async :refer [chan put! go <!]])
  (:import [java.util.function Consumer]
           [com.github.pgasync ResultSet]
           [com.github.pgasync.impl PgRow]))

(set! *warn-on-reflection* true)

(defmacro defasync [name args]
  `(defn ~name [~@args]
     (let [c# (chan 1)]
       (~(symbol (subs (str name) 1)) ~@args #(put! c# [%1 %2]))
       c#)))

(defmacro consumer-fn [[param] body]
  `(reify Consumer (accept [_# ~param]
                     (~@body))))

(defn result->map [^ResultSet result]
  (let [columns (.getColumns result)
        row->map (fn [^PgRow row rowmap ^String col]
            (assoc rowmap (keyword (.toLowerCase col)) (.get row col)))]
    {:updated (.updatedRows result)
     :rows (vec (map (fn [row]
                           (reduce (partial row->map row) {} columns))
                         result))}))

(defn- list-columns [data]
  (if (map? data)
    (map name (keys data))
    (recur (first data))))

(defn- list-params [start end]
  (for [i (range start end)] (str "$" i)))

(defn- list-params-seq [datas]
  (if (map? datas)
    (list-params 1 (inc (count datas)))
    (let [size (count (first datas))
          max  (inc (* (count datas) size))]
      (string/join ", " (map
                         #(str "(" (string/join ", " %) ")")
                         (partition size (list-params 1 max)))))))

(defn create-insert-sql [{:keys [table returning]} datas]
  (str "INSERT INTO " table " ("
       (string/join ", " (list-columns datas))
       ") VALUES "
       (list-params-seq datas)
       (when returning
         (str " RETURNING " returning))))

(defn create-update-sql [{:keys [table returning where]} data]
  (str "UPDATE " table
       " SET ("
       (string/join "," (list-columns data))
       ")=("
       (string/join "," (list-params (count where) (+ (count where) (count data))))
       ") WHERE " (first where)
       (when returning
         (str " RETURNING " returning))))

(defn async-sql-bindings
  "Converts bindings x (f) to [x err] (if [err] [nil err] (<! (f)))"
  [bindings err]
  (let [vars (map (fn [v]
                    [v err])
                  (take-nth 2 bindings))
        fs   (map (fn [f]
                    `(if ~err [nil ~err] (<! ~f)))
                  (take-nth 2 (rest bindings)))]
    (list* [err err] [nil nil] (interleave vars fs))))
