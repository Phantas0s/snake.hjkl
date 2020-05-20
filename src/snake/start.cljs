(ns snake.start
  (:require [snake.core :as c]))

(def game-speed 150)

(defn game-loop!
  "Main game loop"
  [total-time delta-time]
  (if (< total-time (+ delta-time game-speed))
    (js/window.requestAnimationFrame (fn [ts] (game-loop! ts delta-time)))
    (do (c/tick!)
        (js/requestAnimationFrame
         (fn [ts] (game-loop! ts (js/window.performance.now)))))))

(defn init
  []
  (c/init)
  (game-loop! 0 (js/window.performance.now)))

(init)
