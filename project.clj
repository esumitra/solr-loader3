(defproject solr-loader3 "0.1.0-SNAPSHOT"
  :description "CM quick solr loader"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [;; core
                 [org.clojure/clojure "1.7.0-alpha2"] ;; bleeding edge!!
                 [org.clojure/tools.reader "0.7.4"]
                 [org.clojure/data.json "0.2.5"]
                 [com.taoensso/timbre "3.2.1"]
                 [org.clojure/tools.cli "0.3.1"]
                 ;; async
                 ;; [org.clojure/core.async "0.1.303.0-886421-alpha"]
                 [org.clojure/core.async "0.1.338.0-5c5012-alpha"] 
                 [http-kit "2.1.18"]
                 [cheshire "5.3.1"]
                 ;; JDBC-SQL
                 [org.clojure/java.jdbc "0.3.5"]
                 [com.mchange/c3p0 "0.9.2.1"]
                 [com.oracle/ojdbc6 "11.1.0.7.0"]
                 ]

  :main solr-loader3.main
  :profiles {:production
             {:env
              {:production true}}
             ;; run "lein doc" to generate docs
             :dev
             {:plugins [[lein-bin "0.3.4"]
                        [codox "0.8.10"]
                        [cider/cider-nrepl "0.7.0"]]}
             :uberjar
             {:aot :all}}
  )
