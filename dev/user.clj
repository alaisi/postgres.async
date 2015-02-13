(ns user
  (:require [postgres.async :as pg]
            [clojure.tools.namespace.repl :as repl]))

(defn test-db []
  (pg/open-db {:hostname "localhost"
               :database "postgres"
               :username "postgres"
               :password "postgres"}))

(defn reload []
  (repl/refresh))
