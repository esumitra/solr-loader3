(ns solr-loader3.threads
  "thread functions to run tasks on scheduler"
  (:require
   [taoensso.timbre :as timbre])
  (:import
   (java.util.concurrent Semaphore ExecutorService Executors TimeUnit)))

(timbre/refer-timbre)
;;; semaphores for record counts
;;(def io-records (atom nil))
;;; task scheduler functions
;;(def task-pool (atom nil))

;;; for read thread
(defn shutdown-io-taskpool
  "stop processing io tasks and shutdown task pool"
  [task-pool]
  (info "shutting down io thread pool ...")
  (.shutdownNow task-pool)
  (.awaitTermination task-pool 60 TimeUnit/SECONDS))

(defn init-io-taskpool
  "initializes threads for IO reads and writes"
  [num-threads num-records]
  (let [io-records (Semaphore. num-records true)
        task-pool (Executors/newFixedThreadPool num-threads)]
    (info "initialized IO thread pool ...")
    [task-pool io-records]))


(defn execute-io-task
  "runs io task function on io thread pool"
  [task-pool task-fn]
  (.execute task-pool task-fn))

;;; semaphore functions
;;; simplify with macro
(defn execute-function-with-semaphore
  "executes input function after acquiring semaphore
   releases semaphore upon execution"
  [sem fn & args]
  (.acquire sem)
  (apply fn args)
  (.release sem))

(defn execute-function-with-rowlock-acquire
  "executes a function with the supplied row lock acquired"
  [lock fn & args]
  (.acquire lock)
  (apply fn args))

(defn execute-function-with-rowlock-release
  "executes a function and releases the supplied row lock"
  [lock fn & args]
  (apply fn args)
  (.release lock))