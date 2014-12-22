(ns solr-loader3.upload
  "functions for uploading data"
  (:require [solr-loader3.utils :as cutils]
            [solr-loader3.threads :as iotask]
            [clojure.data.json :as json]
            [org.httpkit.client :as http]
            [clojure.core.async :as async]
            [taoensso.timbre :as timbre]))

(timbre/refer-timbre)

(defn wait-for-posts
  "blocks until all messages are processed"
  []
  (async/<!!
   (async/go-loop
    []
    (recur)))
  )

;; Solr HTTP options
(def SolrHTTPOpts
  {
   ;;:url "http://localhost:8983/solr/collection1/update/json"
   ;;:method :post
   :headers {"Content-type" "application/json"}
   :query-params {"commit" "true"}
   :follow-redirects false
   :keepalive 100
   :timeout 86400000
   })

(defn uppercase-keys
  "converts keys to uppercase 
   {:contract_name 1} -> {:CONTRACT_NAME 1}"
   [map]
   (into {} (for [[k v] map]
              (if (= k :id)
                [k v]
                [(-> k name clojure.string/upper-case keyword) v]))))

;; TODO pass counter to callback
(defn upload-callback
  "upload call back function - prints '.' for success 'x' for failure"
  [{:keys [status headers body error]}]
  (if error
    (print "x")
    (print "."))
  (flush))

;; sample record
;; {"NAME":"test-pda2","CLASS":"NDA","STATUS":"Draft","CONTRACT_NUMBER":4,"ID":"233de02849c04e5ab457cdf87d0d59ea"}
;; {:NAME "test-pda2" :CLASS "NDA" :STATUS "Draft" :CONTRACT_NUMBER 4 :ID "233de02849c04e5ab457cdf87d0d59ea"}
(defn upload-record-async
  "uploads a single edn record to the Solr url asynchronously"
  [url record]
  (let [data (json/write-str [record])
        opts (merge SolrHTTPOpts {:body data})]
    #_(info "url: " url \newline "data: " data)
    #_(print ".")
    (http/post url opts upload-callback)))

(defn upload-record-sync
  "uploads a single edn record to the Solr url synchronously"
  [url record]
  (let [data (json/write-str [record])
        opts (merge SolrHTTPOpts {:body data})
        {:keys [status headers body error] :as resp} @(http/post url opts)]
    (if error
      (print "x")
      (print "."))
    (flush)
    ))

(defn start-sync-uploader
  "starts channel listener to upload data records synchronously
a new subscriber channel is started on input pub channel, locks are released as
records are uploaded to the provided url"
  [pub lock counter url]
  (let [chan-upload (async/chan)]
    (async/sub pub :data chan-upload)
    (async/go-loop
     []
     (let [d (async/<! chan-upload)
           d2 (uppercase-keys d)]
       (iotask/execute-function-with-rowlock-release
         lock upload-record-sync url d2)
       (swap! counter dec))
     (recur))))

;; TODO pass counter to callback
(defn start-async-uploader
  "starts channel listener to upload data records asynchronously
a new subscriber channel is started on input pub channel, locks are released as
records are uploaded to the provided url"
  [pub lock counter url]
  (let [chan-upload (async/chan)]
    (async/sub pub :data chan-upload)
    (async/go-loop
     []
     (let [d (async/<! chan-upload)
           d2 (uppercase-keys d)]
       (iotask/execute-function-with-rowlock-release
         lock upload-record-async url d2))
     (recur))))
