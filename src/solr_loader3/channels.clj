(ns solr-loader3.channels
  "functions to initialize channels, setup pub-sub messages and message handlers"
  (:require
   [taoensso.timbre :as timbre]
   [clojure.core.async :as async]
   [clojure.data.json :as json]
   [solr-loader3.utils :as cutils]
   [solr-loader3.threads :as iotask]))

(timbre/refer-timbre)

;; pub-sub channels for data records
;; TODO create/return channels and pass in channels
;; instead of global atoms 

(defn get-data-pub
  "creates and returns data channel and publish data que"
  []
  (let [chan-data (async/chan)
        pub-data (async/pub chan-data (constantly :data))]
    [chan-data pub-data]))

(defn start-sub-logger
  "starts the logging subscriber to the data pub queue"
  [pub]
  (let [chan-log (async/chan)]
    (async/sub pub :data chan-log)
    (async/go-loop
     []
     (let [d (async/<! chan-log)]
       (println "data:" \tab d)
       (recur)))))

(defn start-sub-logger-with-locks
  "starts the logging subscriber to the data pub queue; releases lock when logging complete"
  [pub lock]
  (let [chan-log (async/chan)]
    (async/sub pub :data chan-log)
    (async/go-loop
     []
     (let [d (async/<! chan-log)]
       (iotask/execute-function-with-rowlock-release
         lock
         (fn [] (println "data:" \tab d)))
       (recur)))))

(defn wait-for-upload-complete
  "waits until all records are uploaded; uses passed in counter "
  [counter]
  (loop []
    (async/<!! (async/timeout 5000))
    (info (str \newline "records in que: " @counter \newline))
    (recur)))

(defn post-data
  "posts input data to input-que"
  [ch-data data]
  (async/put! ch-data data))