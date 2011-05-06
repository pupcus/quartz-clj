(ns quartz-clj.core)

(def *scheduler* (.getScheduler (org.quartz.impl.StdSchedulerFactory.)))

;; private support functions

(defn- extract-function-name [string]
  (.substring string (inc (.lastIndexOf string "."))))

(defn- job-name [name]
  (str "job-name-" name))

(defn- job-group [name]
  (str "job-group-" name))

(defn- trigger-name [name]
  (str "trigger-name-" name))

(defn- trigger-group [name]
  (str "trigger-group-" name))

(defn- make-job-detail [name classname]
  (let [detail (org.quartz.JobDetail. (job-name name) (job-group name) classname)]
    (doto detail
      (.setRequestsRecovery false))))

(defn- make-simple-trigger [name start end repeat interval]
  (org.quartz.SimpleTrigger. (trigger-name name) (trigger-group name) (job-name name) (job-group name) start end repeat interval))

(defn- make-cron-trigger [name cron-expression]
  (org.quartz.CronTrigger. (trigger-name name) (trigger-group name) (job-name name) (job-group name) cron-expression))


(defn- add-job [detail trigger]
  (let [trigger-name (.getName trigger)
        trigger-group (.getGroup trigger)
        old-trigger (.getTrigger *scheduler* trigger-name trigger-group)]
    (.addJob *scheduler* detail true)
    (if (nil? old-trigger)
      (.scheduleJob *scheduler* trigger)
      (.rescheduleJob *scheduler* trigger-name trigger-group trigger))))

(defn- create-job* [name _class trigger datamap]
  (let [detail (make-job-detail name _class)]
    (if datamap
      (.setJobDataMap detail datamap))
    (add-job detail trigger)))

;; public user functions

(defn create-interval-job [name _class start end repeat interval & [datamap]]
  (let [trigger (make-simple-trigger name start end repeat interval)]
    (create-job* name _class trigger datamap)))

(defn create-cron-job [name _class cron & [datamap]]
  (let [trigger (make-cron-trigger name cron)]
    (create-job* name _class trigger datamap)))

(defn remove-job [name]
  (let [job-name (job-name name)
        job-group (job-group name)]
    (if-let [job-detail (.getJobDetail *scheduler* job-name job-group)]
      (.deleteJob *scheduler* job-name job-group))))

(defn start []
  (doto *scheduler*
    (.start)))

(defn pause []
  (doto *scheduler*
    (.pauseAll)))

(defn resume []
  (doto *scheduler*
    (.resumeAll)))

(defn shutdown []
  (doto *scheduler*
    (.shutdown)))

;; macros for defining quartz jobs

(defmacro defjob* [interface _class args & body]
  (let [function-name (gensym (extract-function-name (str _class)))]
    `(do
       (gen-class
        :name ~_class
        :implements [~interface])
       (defn- ~(symbol function-name) ~args
         ~@body)
       (defn ~(symbol (name _class) "-execute") [this# context#]
         (~(symbol function-name) context#)))))

(defmacro defjob [_class args & body]
  `(defjob* org.quartz.Job ~_class ~args ~@body))

(defmacro defstatefuljob [_class args & body]
  `(defjob* org.quartz.StatefulJob ~_class ~args ~@body))


