quartz-clj
==========

clojure utils/tools for starting/manipulating quartz tasks

This code started out as a demonstration for a colleague of how useful clojure could be when the need to interop with java libraries arose.  We use quartz quite a bit in our java code and I thought the way clojure allows a very dynamic approach to the quartz runtime as well as the ease with which jobs/tasks could be created and manipulated would impress.  It did not.  Unfortunately, in the end s/he does not like clojure (yet ;-) ).  Then, some time later, Ian Eslick took the orignal code and vastly improved it (much thanks to him for both his interest and his energy).  And that is where things are at the moment. If you have any suggestions or find any issues please let me know. Enjoy.

building the project
--------------------

    mvn clean ; mvn

usage
-----

This is now deployed on clojars

For lein

    [quartz-clj "0.0.3"]

For maven

    <dependency>
        <groupId>quartz-clj</groupId>
        <artifactId>quartz-clj</artifactId>
        <version>0.0.3</version>
    </dependency>


TODO: more examples

    ;; loading the library

    (require '[quartz-clj.core :as qcore] :reload-all :verbose)

    ;; start the schduler

    (qcore/start)

    ;; define the job to execute when the trigger fires

    (qcore/defjob TestJob [context]
      (let [jobId (.get (.getJobDataMap (.getJobDetail context)) "jobId")]
        (println (format "A quartz task with jobId=[%s]" jobId))))

    ;; schedule the job

    (qcore/schedule-job (qcore/create-job "test-job" user.TestJob :datamap {"jobId" 1})
                       (qcore/create-trigger "test-trigger" :schedule (qcore/create-schedule "test-schedule" :repeat 10 :seconds 2)))

you should see the TestJob firing every 2 seconds for 10 executions.

NOTE: if you want to create jobs that are backed by a jdbc-jobstore (I personally needed this functionality as well), you must compile the job definitions to java classes.  If you figure out how to get quartz to instantiate the jobs some other way let me know :-).  At the slime repl I would compile the defjob or defstatefuljob code (ctrl-c ctrl-k) but quartz could not find the classes until I used

    (compile 'scratch.TestJob)

which put .class files in the classes directory.  THEN things started working.  Also note, you can 'recompile' the job definitions dynamically, change the jobs, etc., and things keep right on churning.  It is pretty cool.  See the source code (there is not a lot to it) for more on the options, the functions, and how to do certain things.

TODO:  examples of setting things up to work with a jdbc datastore

contributors
------------

Ian Eslick  Many thanks to Ian for his work on the code base.
  
License
-------

Copyright (c) pupcus.org

Distributed under the Eclipse Public License, the same as Clojure.

USE AS IS. NO GUARANTEES/WARRANTY/ETC

