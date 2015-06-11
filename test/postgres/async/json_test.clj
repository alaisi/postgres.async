(ns postgres.async.json-test
  (:require [postgres.async :refer :all]
            [postgres.async.json]
            [clojure.test :refer :all]
            [postgres.async-test :refer [db-fixture wait *db*]]))

(use-fixtures :each db-fixture)

(deftest json-queries

  (testing "json is returned as json using keywords"
    (let [rs (wait (query! *db*
                           ["select '{\"name\":\"demo\",\"c\":[1,2]}'::JSON as j"]))]
      (is (= "demo" (get-in rs [0 :j :name])))
      (is (= [1 2] (get-in rs [0 :j :c])))))

  (testing "json array parameter is returned as vec"
        (let [rs (wait (query! *db* ["select $1::JSON as j" ["a" "b"]]))]
          (is (= ["a" "b"] (get-in rs [0 :j])))))

  (testing "json parameter is returned as map"
    (let [rs (wait (query! *db* ["select $1::JSON as j" {:a "b"}]))]
      (is (= {:a "b"} (get-in rs [0 :j]))))))

(deftest jsonb-queries

  (testing "jsonb array parameter is returned as vec"
        (let [rs (wait (query! *db* ["select $1::JSONB as j" ["a" [1 2]]]))]
          (is (= ["a" [1 2]] (get-in rs [0 :j])))))

  (testing "jsonb parameter is returned as map"
    (let [rs (wait (query! *db* ["select $1::JSONB as j" {:a nil :b true :c "s"}]))]
      (is (nil? (get-in rs [0 :j :a])))
      (is (= true (get-in rs [0 :j :b])))
      (is (= "s" (get-in rs [0 :j :c]))))))

