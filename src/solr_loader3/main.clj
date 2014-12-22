(ns solr-loader3.main
  "main class for solr loader"
  (:require
   [solr-loader3.core :as solr-core]
   [clojure.tools.cli :refer [parse-opts]])
  (:gen-class))

(defn show-usage
  [o summary]
  (println "task:" o)
  (println "Options:" \newline summary))

(def cli-options
  [["-h" "--help" "Displays help on command usage"]
   ["-u" "--upload" "upload CM data to solr"]
   ["-t" "--test-conn CONFIGFILE" "test connection"]
   ["-k" "--test-tasks CONFIGFILE" "test db tasks"]
   ["-p" "--profile" "runs DB profiling tests"]
   ["-s" "--security-upload CONFIGFILE" "uploads security data to solr"]])

(defn timed-function
  "prints elapsed time for function execution"
  [& args]
  (time (apply (first args) (rest args))))

(defn -main
  "main function entry point"
  [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (cond
     (:help options) (timed-function show-usage "help" summary)
     (:upload options) (show-usage "upload" summary)
     (:test-conn options) (solr-loader3.core/task-test-connection (:test-conn options))
     (:test-tasks options) (solr-loader3.core/task-test-db-tasks (:test-tasks options))
     :else (show-usage "none" summary))))


