(ns quartz-clj.core
  (:import [org.quartz JobBuilder]))

<<<<<<< HEAD
(defonce ^:dynamic *scheduler* (atom nil))
=======
;;
;; NOTE:
;; on reload/recompile to reset *scheduler* to currently running scheduler run this at the repl:
;;   (swap! *scheduler* (fn [old] (org.quartz.impl.StdSchedulerFactory/getDefaultScheduler)))
;;
(def ^:dynamic *scheduler* (atom nil))
>>>>>>> mdp/master

;; PRIVATE: Handle options

(defn- as-data-map [m]
  (org.quartz.JobDataMap. m))

(defn- configure-job [job [option value]]
  (case option
	:recovery (.requestRecovery job value)
	:durably (.storeDurably job value)
	:description (.withDescription job value)
        :datamap (.usingJobData job (as-data-map value)) ;; value as clojure hash-map
	true (throw (java.lang.Error. (format "Unrecognized job option %s" option)))))


(defn- configure-simple-schedule [schedule [option value]]
  (case option
	:repeat (.withRepeatCount schedule value)
	:hours (.withIntervalInHours schedule value)
	:minutes (.withIntervalInMinutes schedule value)
	:seconds (.withIntervalInSeconds schedule value)
	:forever (.repeatForever schedule)
	true (throw (java.lang.Error. (format "Unrecognized schedule option %s" option)))))

(defn- configure-trigger [trigger [option value]]
  (case option
	:description (.withDescription trigger value)
	:priority (.withPriority trigger value)
	:schedule (.withSchedule trigger value)
	:start (.startAt trigger value)
	:end (.endAt trigger value)
	true (throw (java.lang.Error. (format "Unrecognized trigger option %s" option)))))

(defn- as-clojure-key [key]
  [(.getName key) (.getGroup key)])

(defn- as-job-key 
  "Clojure task names can be formatted as pairs [name group] => group.name or
   singletons name => DEFAULT.name"
  [name]
  (if (sequential? name)
    (let [[name group] name]
      (org.quartz.JobKey/jobKey name group))
    (org.quartz.JobKey/jobKey name)))

(defn- as-trigger-key 
  "Clojure task names can be formatted as pairs [name group] => group.name or
   singletons name => DEFAULT.name"
  [name]
  (if (sequential? name)
    (let [[name group] name]
      (org.quartz.TriggerKey/triggerKey name group))
    (org.quartz.TriggerKey/triggerKey name)))

;; PUBLIC: Work with scheduler

(defn start []
  (when @*scheduler*
    (throw (java.lang.Error. "Scheduler already active")))
  (swap! *scheduler*
	 (fn [old]
	   (.getScheduler (org.quartz.impl.StdSchedulerFactory.))))
  (doto @*scheduler*
    (.start)))

(defn pause []
  (if @*scheduler*
    (doto @*scheduler*
      (.pauseAll))
    (throw (java.lang.Error. "Scheduler not started"))))

(defn resume []
  (if @*scheduler*
    (doto @*scheduler*
      (.resumeAll))
    (throw (java.lang.Error. "Scheduler not started or not paused"))))

(defn shutdown []
  (when @*scheduler*
    (doto @*scheduler*
      (.shutdown))
    (swap! *scheduler* (fn [old] nil))))

(defn all-groups []
  (seq (.getJobGroupNames @*scheduler*)))

(defn group-jobs [groupname]
  (map as-clojure-key
       (.getJobKeys @*scheduler* (org.quartz.impl.matchers.GroupMatcher/jobGroupEquals groupname))))

(defn all-jobs []
  (mapcat group-jobs (all-groups)))

(defn job-detail [name]
  (.getJobDetail @*scheduler* (as-job-key name)))

(defn job-class [name]
  (.getJobClass (job-detail name)))

(defn job-data [name]
  (.getJobDataMap (job-detail name)))

(defn remove-job [name]
  (let [key (as-job-key name)]
    (if-let [job-detail (.getJobDetail @*scheduler* key)]
      (.deleteJob @*scheduler* key))))

(defn trigger [name]
  (.getTrigger @*scheduler* (as-trigger-key name)))

;; PUBLIC: Low Level Public Interface

(defn create-job [name class & {:as options}]
  (let [job (org.quartz.JobBuilder/newJob class)]
    (.withIdentity job (as-job-key name))
    (dorun (map (partial configure-job job) options))
    (.build job)))

(defn simple-schedule [& {:as options}]
  (let [schedule (org.quartz.SimpleScheduleBuilder/simpleSchedule)]
    (dorun (map (partial configure-simple-schedule schedule) options))
    schedule))

(defn cron-schedule [expr & {:as options}]
  (let [schedule (org.quartz.CronScheduleBuilder/cronSchedule expr)]
    (when-let [tz (:tz options)]
      (.cronSchedule schedule tz))
    schedule))

(defn create-trigger [name & {:as options}]
  (let [trigger (org.quartz.TriggerBuilder/newTrigger)]
    (.withIdentity trigger (as-trigger-key name))
    (dorun (map (partial configure-trigger trigger) options))
    (.build trigger)))

(defn schedule-job [job trigger]
  (.scheduleJob @*scheduler* job trigger))

(defn reschedule-job [job trigger]
  (let [triggers (doto (.getTriggersOfJob @*scheduler* (.getKey job)) (.add trigger))]
    (.scheduleJobs @*scheduler* (java.util.HashMap. {job triggers}) true)))

(defn reset-data-map [job hash]
  (doto job (.setJobDataMap (as-data-map hash))))

(defn update-job [job]
  (.addJob @*scheduler* job true))

;;
;; PUBLIC: High Level Interface (and examples of using low-level API)
;;

(defn schedule-task [name class schedule & {:as options}]
  (schedule-job
   (create-job name class)
   (apply create-trigger name
	  (flatten
	   (seq (assoc options
		  :schedule schedule
		  :start (or (:start options) (java.util.Date.))))))))

(defn schedule-repeated-task [name class count seconds & {:as options}]
  (schedule-job
   (create-job name class)
   (apply create-trigger name
	  (flatten
	   (seq (assoc options
		  :schedule (simple-schedule :repeat count :seconds seconds)
		  :start (or (:start options) (java.util.Date.))))))))

(defn schedule-cron-task [name class cron-expr & {:as options}]
  (schedule-job
   (apply create-job name class (flatten (seq (select-keys options :description))))
   (apply create-trigger name
	  (flatten (seq (assoc options
                          :schedule (apply cron-schedule cron-expr (select-keys options :tz))))))))

;; Macros for defining quartz jobs

(defmacro defjob [_class args & body]
  `(defrecord ~_class []
     org.quartz.Job
     (execute [this ~@args]
              ~@body)))

;;
;; WARNING:
;; org.quartz.StatefulJob is a deprecated interface in 2.x
;; to make a quartz job stateful it is best to apply the
;; @DisallowConcurrentExecution annotation BUT we can't do that in
;; clojure :-(
;;
;; the interface IS still around and is our only option at the moment
;; this macro could go away in the future and pin you to a particular
;; version of this library.
;;

(defmacro defstatefuljob [_class args & body]
  `(defrecord ~_class []
     org.quartz.StatefulJob
     (execute [this ~@args]
              ~@body)))


;; Listeners

(defn add-listener
  "Adds a listener class to the scheduler that is called
   whenever the condition matches"
  [listener matcher]
  (let [mgr (.getListenerManager @*scheduler*)]
    (.addJobListener mgr listener matcher)))

(defn remove-listener
  [name]
  (let [mgr (.getListenerManager @*scheduler*)]
    (.removeJobListener mgr name)))

(defn match-on-job [name group]
  (org.quartz.impl.matchers.KeyMatcher/keyEquals
   (org.quartz.JobKey. name group)))

(defn match-on-group [group]
  (org.quartz.impl.matchers.GroupMatcher/groupEquals group))


;; Example:
;;
;;(deflistener MyListener
;;  (:executing [ctx] <body>)
;;  (:vetoed [ctx] <body>)
;;  (:executed [ctx except] <body>)

(defmacro deflistener [_class & methods]
  (letfn [(get-method [name] (first (filter #(= (first %) name) methods)))
          (method-vars [name] (second (get-method name)))
          (method-body [name] (nthrest (get-method name) 2))]
    `(defrecord ~_class [~'name]
       org.quartz.JobListener
       (getName [~'this] (:name ~'this))
       (jobToBeExecuted [~'this ~@(method-vars :executing)]
         ~@(method-body :executing))
       (jobExecutionVetoed [~'this ~@(method-vars :vetoed)]
         ~@(method-body :vetoed))
       (jobWasExecuted [~'this ~@(method-vars :executed)]
         ~@(method-body :executed)
         ))))
