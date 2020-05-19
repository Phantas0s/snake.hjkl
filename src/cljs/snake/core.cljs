(ns snake.core
  (:require [goog.dom :as dom]
            [goog.events :as events]
            [goog.events.KeyCodes]
            [goog.events.EventType]
            [snake.levels :refer [get-walls]]
            [clojure.pprint :refer [pprint]]))

(enable-console-print!)

; TODO - Implement watcher for draw-level / points?
; TODO - See how to use core.async to use on event handlers

;; ------------------------------
;; Constants

(def canvas (let [width 640
                  height 640
                  unit-width 32
                  unit-height 32
                  canvas (dom/getElement "canvas")]
              {:width width
               :height height
               :unit/width unit-width
               :unit/height unit-height
               :max-x (/ width unit-width)
               :max-y (/ height unit-height)
               :background-color "#303030"
               :element canvas
               :ctx (.getContext canvas "2d")}))

(def message-element (.getElementById js/document "message"))
(def message-box-element (.getElementById js/document "message-box"))
(def hud-score-element (.getElementById js/document "score"))
(def hud-level-element (.getElementById js/document "level"))

(def next-level-score 100)
(def food-points 10)
(def food-color "#ffffff")
(def snake-color "#d0d0d0")
(def wall-color "#000000")
(def game-speed 150)

(def directions {:up [0 -1]
                 :down [0 1]
                 :left [-1 0]
                 :right [1 0]})


;; ------------------------------
;; Mutable states


(defonce states
  (atom {})) ; states are merged with defaults at game-reset!

(def snake-defaults {:snake/body '([5 9] [4 9] [3 9])
                     :snake/direction [1 0]
                     :snake/direction-queue []
                     :snake/food nil})

(def defaults (merge {:game/key-lock false
                      :game/level 1
                      :game/pause true
                      :game/score 0} snake-defaults))

;; ------------------------------
;; To debug

(defn print-states!
  "Print current game states on console.(For debugging purpose)"
  []
  (.log js/console "-------------------------------")
  (.log js/console (with-out-str (pprint @states)))
  (.log js/console "-------------------------------\n"))

;; ------------------------------
;; Draw functions

(defn draw-rect!
  "Draw a rect on canvas"
  [{:keys [:ctx :unit/width :unit/height]} [x y] color]
  (aset ctx "fillStyle" color)
  (.fillRect ctx
             (* x width)
             (* y height)
             width
             height))

(defn draw-circle!
  "Draw a circle on canvas"
  [{:keys [:ctx :unit/width :unit/height]} [x y] color]
  (aset ctx "fillStyle" color)
  (.beginPath ctx)
  (.arc ctx
        (+ (/ width 2) (* x width))
        (+ (/ height 2) (* y height))
        (/ height 2)
        0
        (* 2 Math/PI)
        false)
  (.fill ctx))

(defn update-text!
  "Draw some text"
  [element text]
  (set! (.-innerHTML element) text))

(defn draw-wall-level!
  [canvas level wall-color]
  (doseq [w (get-walls level)]
    (draw-rect! canvas w wall-color)))

(defn canvas-reset!
  [{:keys [:background-color :ctx :width :height]}]
  (aset ctx "fillStyle" background-color)
  (.fillRect ctx
             0
             0
             width
             height))

(defn canvas-resize!
  "Resize the canvas according to the states"
  [{:keys [:element :width :height]}]
  (.setAttribute element "width" width)
  (.setAttribute element "height" height))


;; ------------------------------
;; Message box functions


(defn message-box-hide!
  []
  (set! (.-display (.-style message-box-element)) "none"))

(defn message-box-show!
  ([]
   (set! (.-display (.-style message-box-element)) "block"))
  ([text]
   (set! (.-display (.-style message-box-element)) "block")
   (update-text! message-element text)))

;; ------------------------------
;; Helpers

(defn axis-add
  [[x1 y1] [x2 y2]]
  [(+ x1 x2) (+ y1 y2)])

(defn axis-equal?
  [x y]
  (= x y))

;; ------------------------------
;; Collision

(defn snake-collision?
  [[x y] body]
  (some #(axis-equal? [x y] %) body))

(defn wall-collision?
  [[x y]]
  (let [{:keys [:game/level]} @states
        walls (get-walls level)]
    (some #(= [x y] %) walls)))

;; ------------------------------
;; Key Mechanics


(defn keycodes
  [k]
  (get (js->clj goog.events.KeyCodes :keywordize-keys true) k))

(defn keycode->direction
  "Convert JavaScript keycode to direction array"
  [{:keys [:up :down :left :right]} keycode]
  (get {(keycodes :K) up
        (keycodes :J) down
        (keycodes :H) left
        (keycodes :L) right}
       keycode nil))

;; ------------------------------
;; Game Mechanics

(defn opposite-direction?
  [dir-1 dir-2]
  (= [0 0] (axis-add dir-1 dir-2)))

(defn out-of-boundary?
  "Check if axis is exceed the game board boundary."
  [max-x max-y [x y]]
  (or (>= x max-x) (< x 0) (>= y max-y) (< y 0)))

(defn eat-food?
  [{:keys [:snake/food]} [x y]]
  (= [x y] food))

(defn next-level?
  [{:keys [:game/score :game/level]}]
  (>= score (* level next-level-score)))

(defn lose?
  [{:keys [:max-x :max-y]} snake-head snake-body]
  (or
   (snake-collision? snake-head snake-body)
   (out-of-boundary? max-x max-y snake-head)
   (wall-collision? snake-head)))

(defn random-coords
  [{:keys [:max-x :max-y]}]
  (map vector
       (repeatedly #(rand-int max-x))
       (repeatedly #(rand-int max-y))))

(defn generate-food!
  [canvas body]
  (some #(when-not (or (snake-collision? % body)
                       (wall-collision? %)) %)
        (random-coords canvas)))

(defn level-reset!
  [canvas states level]
  (canvas-reset! canvas)
  (let [food (generate-food! canvas (:body snake-defaults))]
    (draw-circle! canvas food food-color)
    (swap! states assoc-in [:snake/food] food)
    (draw-wall-level! canvas level wall-color)
    (message-box-show! (str "Level " level))))

(defn game-reset!
  [states canvas]
  (swap! states merge defaults)
  (update-text! hud-score-element (:game/score @states))
  (update-text! hud-level-element (:game/level @states))
  (level-reset! canvas states 1))

(defn game-loop!
  "Main game loop"
  [{:keys [:game/score :snake/body :snake/direction :snake/direction-queue]} states tframe last-time]
  (if (< tframe (+ last-time game-speed))
    (js/window.requestAnimationFrame (fn [tframe] (game-loop! @states states tframe last-time)))
    (let [head (axis-add (first body) direction)
          tail (last body)]
      (swap! states assoc-in [:game/key-lock] false)

      (when (lose? canvas head body)
        (game-reset! states canvas))

      (when-not (:game/pause @states)
        (let [next-snake-direction (first direction-queue)]
          (when-not (or (empty? direction-queue) (opposite-direction? next-snake-direction direction))
            (swap! states assoc
                   :snake/direction next-snake-direction
                   :snake/direction-queue (drop 1 (:snake/direction-queue @states)))))
        (draw-rect! canvas head snake-color)
        (if (eat-food? @states head)
          (let [food (generate-food! canvas body)
                new-score (+ score food-points)]
            (swap! states assoc
                   :game/score new-score
                   :snake/body (conj body head)
                   :snake/food food)
            (draw-circle! canvas food food-color)
            (update-text! hud-score-element new-score))

          (do
            (swap! states assoc-in [:snake/body] (drop-last (conj body head)))
            (draw-rect! canvas tail (:background-color canvas)))))

      (when (next-level? @states)
        (let [new-level (inc (:game/level @states))]
          (swap! states merge
                 snake-defaults
                 (assoc {}
                        :game/level new-level
                        :game/pause true))
          (update-text! hud-level-element (:game/level @states))
          (level-reset! canvas states new-level)))

      (js/window.requestAnimationFrame (fn [tframe] (game-loop! @states states tframe (js/window.performance.now)))))))

;; ------------------------------
;; Event handlers


(defn on-retry
  "Executed when close a message-box"
  [event]
  (message-box-hide!)
  (swap! states assoc-in [:game/pause] false))

(defn on-keydown
  "Executed when HJKL is pressed on keyboard"
  [event]
  (when (= (.-keyCode event) goog.events.KeyCodes.ENTER)
    (on-retry event))
  (when (= (.-keyCode event) goog.events.KeyCodes.SPACE)
    (swap! states assoc-in [:game/pause] true)
    (message-box-show! "Break!"))

  (let [{:keys [:snake/direction]} @states
        new-direction (keycode->direction directions (.-keyCode event))]
    (if (:game/key-lock @states)
      (swap! states assoc-in [:snake/direction-queue] (conj (:game/key-queue @states) new-direction))
      (when (and new-direction (not (opposite-direction? direction new-direction)))
        (swap! states assoc
               :snake/direction new-direction
               :game/key-lock true)))))

;; ------------------------------
;; Initialization

(defn init
  "Reset the states, create the events and run the game"
  []
  (canvas-resize! canvas)
  (canvas-reset! canvas)
  (events/listen js/document goog.events.EventType.KEYDOWN on-keydown)
  (events/listen message-box-element goog.events.EventType.CLICK on-retry)
  (game-reset! states canvas)
  (game-loop! @states states (js/window.performance.now) 0))

(init)
