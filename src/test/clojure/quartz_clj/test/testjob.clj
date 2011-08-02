(ns quartz-clj.test.testjob
  (:use quartz-clj.core))

(defjob quartz-clj.test.testjob [context]
  (let [jobId (.get (.getJobDataMap (.getJobDetail context)) "jobId")]
    (println (format "Job Firing with jobId=[%s]" jobId))))
