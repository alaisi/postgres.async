(defproject alaisi/postgres.async "0.7.0-SNAPSHOT"
  :description "Asynchronous PostgreSQL Clojure client"
  :url "http://github.com/alaisi/postgres.async"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :scm {:name "git"
        :url "http://github.com/alaisi/postgres.async.git"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/core.async "0.2.374"]
                 [com.github.alaisi.pgasync/postgres-async-driver "0.7"]
                 [cheshire "5.5.0" :scope "provided"]]
  :lein-release {:deploy-via :clojars}
  :global-vars {*warn-on-reflection* true}
  :target-path "target/%s"
  :profiles {:dev {:source-paths ["dev"]
                   :dependencies [[org.clojure/tools.namespace "0.2.4"]
                                  [org.clojure/java.classpath "0.2.0"]]}})
