postgres.async
==============

Asynchronous PostgreSQL client for Clojure.

## Download

TODO: clojars coordinates

## Setting up a connection pool

A pool of connections to PostgreSQL backend is created with `open-db`. Each connection *pool* starts a single I/O thread used in communicating with PostgreSQL backend.

```clojure
(require '[postgres.async :refer :all])

(def db (open-db {:hostname "db.example.com"
                  :port 5432 ; default
                  :database "exampledb"
                  :username "user"
                  :password "pass"
                  :pool-size 25})) ; default
```

The connection pool is closed with `close-db!`. This closes all open connections and stops the pool I/O thread.

```clojure
(close-db! db)
```

## Running SQL queries

Queries are executed with callback-based functions `query!` `insert!` `update!` `execute!` and [`core.async`](https://github.com/clojure/core.async) channel-based functions `<query!` `<insert!` `<update!` `<execute!`.

Channel-based functions return a channel where query result is put in vector of [result-set exception].

### execute! and query!

All other query functions delegate to `execute!`. This takes a db, a vector of sql string and parameters plus a callback with arity of two.

```clojure
;; callback
(execute! db ["select $1::text" "hello world"] (fn [rs err]
                                                   (prinln rs err))
; nil

;; async channel
(<!! (<query! db ["select name, price from products where id = $1" 1001]))
; [{:updated 0, :rows [{:id 1001, :name "hammer", :price 10]]} nil]

(<!! (<query! db ["select * from foobar"]))
; [nil #<SqlException com.github.pgasync.SqlException: ERROR: SQLSTATE=42P01, MESSAGE=relation "foobar" does not exist>
```

`query!` passes only `:rows` to callback.

```clojure
(<!! (<query! db ["select name, price from products"]))
; [[{:id 1000, :name "screwdriver", :price 15} {:id 1001, :name "hammer", :price 10] nil]

(<!! (<query! db ["select name, price from products where id = $1" 1001]))
; [[{:id 1001, :name "hammer", :price 10] nil]
```

### insert!

Insert is executed with an sql-spec that supports keys `:table` and `:returning`.

```clojure
(<!! (<insert! db {:table "products"} {:name "screwdriver" :price 15}))
; [{:updated 1, :rows []} nil]

(<!! (<insert! db {:table "products" :returning "id"} {:name "hammer" :price 5}))
; [{:updated 1, :rows [{:id 1001}]} nil]
```

### update!

Update is executed with an sql-spec that supports keys `:table` `:retuning` and `where`.

```clojure
(<!! (<update! db {:table "users" :where ["id = $1" 1001}} {:price 6}))
; [{:updated 1, :rows []} nil]
```

## Composition

Channel-returning functions can be composed with `dosql` macro that returns [result-of-body exception].

```clojure
(<!! (go
       (dosql [tx (<begin! db)
               rs (<insert! tx {:table products :returning "id"} {:name "saw"})
               _  (<insert! tx {:table promotions} {:product_id (get-in rs [:rows 0 :id])})
               rs (<query!  tx ["select * from promotions"])
               _  (<commit! tx)]
            {:now-promoting rs})))
; [{:now-promoting [{:id 1, product_id 1002}]} nil]
```

## Custom column types

```clojure
(require '[cheshire.core :as json])

(defmethod from-pg-value com.github.pgasync.impl.Oid/JSON [oid value]
  (json/parse-string (String. value))

(extend-protocol IPgParameter 
  clojure.lang.IPersistentMap
  (to-pg-value [value]
    (.getBytes (json/generate-string value))))

```

## Full example

```clojure
(ns example.core
  (:require [postgres.async :refer :all]
            [clojure.core.async :refer [go <!!]]))


(def db (open-db {:hostname "localhost"
                  :port 5432
                  :database "postgres"
                  :username "postgres"
                  :password "postgres"
                  :pool-size 20}))

(<!! (<insert! db {:table "products"} {:name "screwdriver" :price 15}))
; [{:updated 1, :rows []} nil]

(<!! (<insert! db {:table "products" :returning "id"} {:name "hammer" :price 5}))
; [{:updated 1, :rows [{:id 1001}]} nil]

(<!! (<query! db ["select name, price from products"]))
; [[{:id 1000, :name "screwdriver", :price 15} {:id 1001, :name "hammer", :price 10] nil]

(<!! (<query! db ["select name, price from products where id = $1" 1001]))
; [[{:id 1001, :name "hammer", :price 10] nil]

(<!! (<query! db ["select * from foobar"]))
; [nil #<SqlException com.github.pgasync.SqlException: ERROR: SQLSTATE=42P01, MESSAGE=relation "foobar" does not exist>

(<!! (<update! db {:table "users" :where ["id = $1" 1001}} {:price 6}))
; [{:updated 1, :rows []} nil]

(<!! (<execute! db ["select 1 as anything"]))
; [{:updated 0, :rows [{:anything 1}]} nil]

;; Asynchronous composition. dosql returns [nil exception] on first error
(<!! (go
       (dosql [tx (<begin! db)
               rs (<insert! tx {:table products :returning "id"} {:name "saw"})
               _  (<insert! tx {:table promotions} {:product_id (get-in rs [:rows 0 :id])})
               rs (<query!  tx ["select * from promotions"])
               _  (<commit! tx)]
            {:now-promoting rs})))
; [{:now-promoting [{:id 1, product_id 1002}]} nil]

(close-db! db)
; nil

;; Extension points for custom column types
(require '[cheshire.core :as json])

(defmethod from-pg-value com.github.pgasync.impl.Oid/JSON [oid value]
  (json/parse-string (String. value))

(extend-protocol IPgParameter 
  clojure.lang.IPersistentMap
  (to-pg-value [value]
    (.getBytes (json/generate-string value))))

```

