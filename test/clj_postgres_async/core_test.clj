(ns clj-postgres-async.core-test
  (:require [clojure.test :refer :all]
            [clojure.core.async :refer [<!! go]]
            [clj-postgres-async.core :refer :all]))

(def ^:private ^:dynamic *db*)

(defn- await [channel]
  (let [[r err] (<!! channel)]
    (if err
      (throw err)
      r)))

(defn- create-tables [db]
  (await (<execute! db ["drop table if exists clj_pg_test"]))
  (await (<execute! db ["create table clj_pg_test (
                           id serial, t varchar(10))"])))

(defn- env [name default]
  (if-let [value (System/getenv name)]
    value
    default))

(defn- db-fixture [f]
  (binding [*db* (open-db {:hostname (env "PG_HOST" "localhost")
                           :port     (env "PG_PORT" 5432)
                           :database (env "PG_DB" "postgres")
                           :username (env "PG_USER" "postgres")
                           :password (env "PG_PASSWORD" "postgres")
                           :pool-size 1})]
    (try
      (create-tables *db*)
      (f)
      (finally (close-db! *db*)))))

(use-fixtures :each db-fixture)

(deftest queries
  (testing "<query! returns rows as map"
    (let [rs (await (<query! *db* ["select 1 as x"]))]
      (is (= 1 (get-in rs [0 :x]))))))

(deftest inserts
  (testing "insert return row count"
    (let [rs (await (<insert! *db* {:table "clj_pg_test"} {:t "x"}))]
      (is (= 1 (:updated rs)))))
  (testing "insert with returning returns generated keys"
    (let [rs (await (<insert! *db* {:table "clj_pg_test" :returning "id"} {:t "y"}))]
      (is (get-in rs [:rows 0 :id])))))

(deftest sql-macro
  (testing "dosql returns last form"
    (is (= "123" (await (go (dosql
                             [rs (<query! *db* ["select 123 as x"])
                              rs (<query! *db* ["select $1::text as t" (:x (first rs))])]
                             (:t (first rs)))))))))
