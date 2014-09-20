(defproject clj-postgres-async "0.1.0-SNAPSHOT"
  :description "Asynchronous PostgreSQL Clojure client"
  :url "http://github.com/alaisi/clj-postgres-async"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/core.async "0.1.338.0-5c5012-alpha"]
                 [com.github.alaisi.pgasync/postgres-async-driver "0.2-SNAPSHOT"]]
  :target-path "target/%s")
