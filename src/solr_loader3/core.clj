(ns solr-loader3.core
  "core function wrappers"
  (:require
   [solr-loader3.db :as db]
   [solr-loader3.channels :as ch]
   [solr-loader3.threads :as iotask]
   [solr-loader3.utils :as cutils]
   [solr-loader3.upload :as up]
   [taoensso.timbre :as timbre]))

;; logging config
(timbre/refer-timbre)
(timbre/set-level! :info)
(timbre/set-config! [:appenders :spit :enabled?] true)
(timbre/set-config! [:shared-appender-config :spit-filename] "solr-loader2.log")

(defn init-db-with-current-config
  "initialize DB from config file and returns current config set"
  [config-file]
  (let [current-config (-> config-file
                           cutils/load-config-file
                           cutils/current-config-set)]
    (debug "current config:" current-config)
    (db/initialize-db current-config)
    current-config))

(defn shutdown-handler
  "handler function to showndown application cleanly by releasing DB connections and thread pools"
  [taskpool]
  (iotask/shutdown-io-taskpool taskpool)
  (db/close-db))

(defn register-shutdown
  "registers shutdown handler to handle ^C"
  [taskpool]
  (.addShutdownHook (Runtime/getRuntime)
                    (Thread. (fn [] (shutdown-handler taskpool)))))

;; lein run -m solr-loader2.core/task-test-connection solr-config.edn
(defn task-test-connection
  "task to test connection from configuration"
  [config-file]
  (init-db-with-current-config config-file)
  (db/test-connection)
  (db/close-db))

(defn- upload-entity-list
  "returns list of entities to be uploaded"
  [config current-config]
  (let [upload-entities (:upload-entities current-config)
        all-entities (:entities config)]
    (filter #(cutils/list-contains? upload-entities (:name %)) all-entities)))

;; lein trampoline run -m solr-loader3.core/task-test-db-tasks solr-config.edn
(defn task-test-db-tasks
  "task to test db task pools"
  [config-file]
  (let [config (cutils/load-config-file config-file)
        current-config (cutils/current-config-set config)
        [task-pool io-recs] (iotask/init-io-taskpool (:num-threads current-config) (:row-buffer-size current-config))]
    (init-db-with-current-config config-file)
    (iotask/execute-io-task task-pool db/test-connection)
    (register-shutdown task-pool)))

;;lein trampoline run -m solr-loader3.core/task-sample-entities solr-config.edn
(defn task-sample-entities
  "task to test db task pools"
  [config-file]
  (let [config (cutils/load-config-file config-file)
        current-config (cutils/current-config-set config)
        entities (upload-entity-list config current-config)
        [task-pool io-recs] (iotask/init-io-taskpool (:num-threads current-config) (:row-buffer-size current-config))]
    (println entities)
    (init-db-with-current-config config-file)
    (register-shutdown task-pool)
    (doseq [ent entities]
      (iotask/execute-io-task task-pool (fn [] (-> ent db/sample-entity db/print-entity-records))))))

;; lein trampoline run -m solr-loader3.core/task-sample-pubsub solr-config.edn
(defn task-sample-pubsub
  "task to test db task pools"
  [config-file]
  (let [config (cutils/load-config-file config-file)
        current-config (cutils/current-config-set config)
        entities (:entities config)
        [chan-data q-pub] (ch/get-data-pub)
        [task-pool io-recs] (iotask/init-io-taskpool (:num-threads current-config) (:row-buffer-size current-config))]
    (init-db-with-current-config config-file)
    (register-shutdown task-pool)
    (ch/start-sub-logger q-pub)
    (doseq [ent entities]
      (iotask/execute-io-task task-pool (fn [] (->> ent db/sample-entity (ch/post-data chan-data)))))))

;; lein trampoline run -m solr-loader3.core/task-readmany-records solr-config.edn
(defn task-readmany-records
  "task to test db task pools"
  [config-file]
  (let [config (cutils/load-config-file config-file)
        current-config (cutils/current-config-set config)
        entities (:entities config)
        [chan-data q-pub] (ch/get-data-pub)
        [task-pool io-recs] (iotask/init-io-taskpool (:num-threads current-config) (:row-buffer-size current-config))
        row-fn #(iotask/execute-function-with-rowlock-acquire
                  io-recs (fn [x] (ch/post-data chan-data x)) %)]
    (init-db-with-current-config config-file)
    (register-shutdown task-pool)
    (ch/start-sub-logger-with-locks q-pub io-recs)
    (doseq [ent entities]
      (iotask/execute-io-task
       task-pool
       (fn []
         (->> ent (db/process-entity-rows row-fn)))))))

;; lein trampoline run -m solr-loader3.core/task-upload-records-sync solr-config.edn
(defn task-upload-records-sync
  "task to upload db records to solr synchronously"
  [config-file]
  (let [config (cutils/load-config-file config-file)
        current-config (cutils/current-config-set config)
        upload-url (:solr-upload-url current-config)
        entities (upload-entity-list config current-config)
        [chan-data q-pub] (ch/get-data-pub)
        [task-pool io-recs] (iotask/init-io-taskpool (:num-threads current-config) (:row-buffer-size current-config))
        record-counter (atom 0)
        row-fn #(iotask/execute-function-with-rowlock-acquire
                  io-recs
                  (fn [x]
                    (ch/post-data chan-data x)
                    (swap! record-counter inc)) %)]
    (info (str "uploading: " entities))
    (init-db-with-current-config config-file)
    (register-shutdown task-pool)
    ;; (ch/start-sub-logger-with-locks q-pub io-recs)
    (up/start-sync-uploader q-pub io-recs record-counter upload-url)
    (doseq [ent entities]
      (info (str "uploading entity: " ent))
      (iotask/execute-io-task
       task-pool
       (fn []
         (->> ent (db/process-entity-rows row-fn)))))
    (ch/wait-for-upload-complete record-counter)))

;; lein trampoline run -m solr-loader3.core/task-upload-records-async solr-config.edn
(defn task-upload-records-async
  "task to upload db records to solr asynchronously"
  [config-file]
  (let [config (cutils/load-config-file config-file)
        current-config (cutils/current-config-set config)
        upload-url (:solr-upload-url current-config)
        entities (upload-entity-list config current-config)
        [chan-data q-pub] (ch/get-data-pub)
        [task-pool io-recs] (iotask/init-io-taskpool (:num-threads current-config) (:row-buffer-size current-config))
        record-counter (atom 0)
        row-fn #(iotask/execute-function-with-rowlock-acquire
                  io-recs
                  (fn [x]
                    (ch/post-data chan-data x)
                    (swap! record-counter inc)) %)]
    (info (str "uploading: " entities))
    (init-db-with-current-config config-file)
    (register-shutdown task-pool)
    ;; (ch/start-sub-logger-with-locks q-pub io-recs)
    (up/start-async-uploader q-pub io-recs record-counter upload-url)
    (doseq [ent entities]
      (info (str "uploading entity: " ent))
      (iotask/execute-io-task
       task-pool
       (fn []
         (->> ent (db/process-entity-rows row-fn)))))
    (ch/wait-for-upload-complete record-counter)))
