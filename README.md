clj-postgres-async
==================

Asynchronous PostgreSQL client for Clojure

## Usage

```clojure
(ns example.core
  (:require [clj-postgres-async.core :refer :all]
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

(<!! (<update! db {:table "users" :where ["id=$1" 1001}} {:price 6}))
; [{:updated 1, :rows []} nil]

(<!! (<execute! db ["select 1 as anything"]))
; [{:updated 0, :rows [{:anything 1}]} nil]

;; Asynchronous composition! dosql returns [nil exception] on first error
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
```

