postgres.async
==============

[![Clojars Project](https://img.shields.io/clojars/v/alaisi/postgres.async.svg)](https://clojars.org/alaisi/postgres.async)

Asynchronous PostgreSQL client for Clojure.

## Download

Add the following to your [Leiningen](http://github.com/technomancy/leiningen) `project.clj`:

![latest postgres.async version](https://clojars.org/alaisi/postgres.async/latest-version.svg)


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

Queries are executed with functions `query!` `insert!` `update!` `execute!`. All functions have two arities that use either callbacks or  a[`core.async`](https://github.com/clojure/core.async) channels.

Channel-based functions return a channel where either query result or exception is put.

### execute! and query!

All other query functions delegate to `execute!`. This takes a db and a seq of sql followed by optional parameters.

```clojure
;; async channel
(<!! (execute! db ["select name, price from products where id = $1" 1001]))
; {:updated 0, :rows [{:id 1001, :name "hammer", :price 10}]}

(<!! (execute! db ["select * from foobar"]))
; #<SqlException com.github.pgasync.SqlException: ERROR: SQLSTATE=42P01, MESSAGE=relation "foobar" does not exist>

;; callback-based higher arity
(execute! db ["select $1::text" "hello world"] (fn [rs err]
                                                   (println rs err))
; nil
```

`query!` passes only `:rows` to callback.

```clojure
(<!! (query! db ["select name, price from products"]))
; [{:id 1000, :name "screwdriver", :price 15} {:id 1001, :name "hammer", :price 10}]

(<!! (query! db ["select name, price from products where id = $1" 1001]))
; [{:id 1001, :name "hammer", :price 10}]
```

### insert!

Insert is executed with an sql-spec that supports keys `:table` and `:returning`.

```clojure
(<!! (insert! db {:table "products"} {:name "screwdriver" :price 15}))
; {:updated 1, :rows []}

(<!! (insert! db {:table "products" :returning "id"} {:name "hammer" :price 5}))
; {:updated 1, :rows [{:id 1001}]}
```

Multiple rows can be inserted by passing a sequence to `insert!`.

```clojure
(<!! (insert! db {:table "products" :returning "id"}
                  [{:name "hammer" :price 5}
                   {:name "nail"   :price 1}]))
; {:updated 2, :rows [{:id 1001} {:id 1002}]}
```

### update!

Update is executed with an sql-spec that supports keys `:table` `:returning` and `:where`.

```clojure
(<!! (update! db {:table "users" :where ["id = $1" 1001}} {:price 6}))
; {:updated 1, :rows []}
```

## Transactions

Starting a transaction with `begin!` borrows a connection from the connection pool until `commit!`, `rollback!` or query failure. Transactional operations must be issued to the transaction instead of db.

See composition below for example.

## Composition

Channel-returning functions can be composed with `dosql` macro that returns result of last form or first exception.

```clojure
(<!! (go
       (dosql [tx (begin! db)
               rs (insert! tx {:table products :returning "id"} {:name "saw"})
               _  (insert! tx {:table promotions} {:product_id (get-in rs [:rows 0 :id])})
               rs (query!  tx ["select * from promotions"])
               _  (commit! tx)]
            {:now-promoting rs})))
; {:now-promoting [{:id 1, product_id 1002}]}
```

## JSON and JSONB

Using JSON types requires `[cheshire "5.5.0"]` and reading the ns `postgres.async.json`. 

```clojure
(require '[postgres.async.json])

(<!! (query! db ["select $1::JSONB" {:hello "world"}]))
; [{:jsonb {:hello "world"}}]
```

## Custom column types

Support for custom types can be added by extending `IPgParameter` protocol and `from-pg-value` multimethod.

```clojure
(extend-protocol IPgParameter 
  com.example.MyHStore
  (to-pg-value [store]
    (.getBytes (str store) "UTF-8")))

(defmethod from-pg-value com.github.pgasync.impl.Oid/HSTORE [oid ^bytes value]
  (my-h-store/parse-string (String. value "UTF-8")))
```

`from-pg-value` can also be used for overriding "core" types. This is especially useful with temporal data types that are by default converted to `java.sql.Date`, `java.sql.Time`, `java.sql.Timestamp`.

```clojure
(defmethod from-pg-value com.github.pgasync.impl.Oid/DATE [oid ^bytes value]
  (java.time.LocalDate/parse (String. value "UTF-8")))
```

## Dependencies

* [postgres-async-driver](https://github.com/alaisi/postgres-async-driver)
* [core.async](https://github.com/clojure/core.async)
* Java 8
