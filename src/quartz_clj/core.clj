(ns quartz-clj.core
  (:import [org.quartz JobBuilder]))

(def ^:dynamic *scheduler* (atom nil))

;; PRIVATE: Handle options

(defn- configure-job [job [option value]]
  (case option
	:recovery (.requestRecovery job value)
	:durably (.storeDurably job value)
	:description (.withDescription job value)
	true (throw (java.lang.Error. (format "Unrecognized job option %s" option)))))


(defn- configure-simple-schedule [sched [option value]]
  (case option
	:repeat (.withRepeatCount sched value)
	:hours (.withIntervalInHours sched value)
	:minutes (.withIntervalInMinutes sched value)
	:seconds (.withIntervalInSeconds sched value)
	:forever (.repeatForever sched)
	true (throw (java.lang.Error. (format "Unrecognized schedule option %s" option)))))

(defn- configure-trigger [trig [option value]]
  (case option
	:description (.withDescription trig value)
	:priority (.withPriority trig value)
	:schedule (.withSchedule trig value)
	:start (.startAt trig value)
	:end (.endAt trig value)
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

;; PUBLIC: Low Level Public Interface

(defn create-job [name class & {:as options}]
  (let [job (org.quartz.JobBuilder/newJob class)]
    (.withIdentity job (as-job-key name))
    (dorun (map (partial configure-job job) options))
    (.build job)))

(defn simple-schedule [& {:as options}]
  (let [sched (org.quartz.SimpleScheduleBuilder/simpleSchedule)]
    (dorun (map (partial configure-simple-schedule sched) options))
    sched))

(defn cron-schedule [expr & {:as options}]
  (let [sched (org.quartz.CronScheduleBuilder/cronSchedule expr)]
    (when-let [tz (:tz options)]
      (.cronSchedule sched tz))
    sched))

(defn create-trigger [name & {:as options}]
  (let [trigger (org.quartz.TriggerBuilder/newTrigger)]
    (.withIdentity trigger (as-trigger-key name))
    (dorun (map (partial configure-trigger trigger) options))
    (.build trigger)))

(defn schedule-job [job trigger]
  (.scheduleJob @*scheduler* job trigger))

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

;; macros for defining quartz jobs

(defmacro defjob [_class args & body]
  `(defrecord ~_class []
     org.quartz.Job
     (execute [this ~@args]
	      ~@body)))

       

