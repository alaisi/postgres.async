(ns clj-postgres-async.core-test
  (:require [clojure.test :refer :all]
            [clojure.core.async :refer [<!! go]]
            [clj-postgres-async.core :refer :all]))

(def ^:dynamic *db*)

(defn- env [name default]
  (if-let [value (System/getenv name)]
    value
    default))

(defn db-fixture [f]
  (binding [*db* (open-db {:hostname (env "PG_HOST" "localhost")
                           :port     (env "PG_PORT" 5432)
                           :database (env "PG_DB" "postgres")
                           :username (env "PG_USER" "postgres")
                           :password (env "PG_PASSWORD" "postgres")
                           :pool-size 1})]
    (try (f)
         (finally (close-db! *db*)))))

(use-fixtures :each db-fixture)

(deftest queries
  (testing "<query! returns rows as map"
    (is (= [[{:x 1}] nil]
           (<!! (<query! *db* ["select 1 as x"]))))))

(deftest sql-macro
  (testing "dosql returns last form"
    (is (= ["123" nil]
           (<!! (go
                 (dosql [rs (<query! *db* ["select 123 as x"])
                         rs (<query! *db* ["select $1::text as t" (:x (first rs))])]
                        (:t (first rs)))))))))
