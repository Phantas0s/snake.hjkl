(defmulti task first)

(defmethod task :default
  [[task-name]]
  (println "Unknown task:" task-name)
  (System/exit 1))

(require
 '[cljs.build.api :as api]
 '[clojure.java.io :as io])

(defn delete-children-recursively! [f]
  (when (.isDirectory f)
    (doseq [f2 (.listFiles f)]
      (delete-children-recursively! f2)))
  (when (.exists f) (io/delete-file f)))

(defmethod task nil
  [_]
  (require 'snake.start)
  (let [out-file "resources/public/main.js"
        out-dir "resources/public/main.out"]
    (println "Building main.js")
    (delete-children-recursively! (io/file out-dir))
    (api/build "src" {:main          'snake.start
                      :optimizations :advanced
                      :output-to     out-file
                      :output-dir    out-dir})
    (delete-children-recursively! (io/file out-dir))
    (println "Build complete:" out-file)
    (System/exit 0)))

(task *command-line-args*)
