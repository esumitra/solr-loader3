(ns solr-loader3.utils
  (:require [clojure.tools.reader.edn :as edn]))

(defn load-config-file
  "loads a configuration file and returns the clojure datastructure for the EDN"
  [path]
  (edn/read-string (slurp path)))

(defn current-config-set
  "returns current config set in config"
  [config]
  (let [current-key (:current-set config)]
    (get-in config [:all-sets current-key])))

(defn list-contains?
  "returns true if list contains the element e"
  [l e]
  (not= nil (some #{e} l)))