(ns snake.core
  (:require [goog.dom :as dom]
            [goog.events :as events]
            [goog.events.KeyCodes]
            [goog.events.EventType]
            [clojure.pprint :refer [pprint]]))

(enable-console-print!)

(def states
  (atom {;; canvas object
         :game/speed 450
         :canvas/element  (dom/getElement "canvas")
         :canvas/container-element (dom/getElement "#main")
         :canvas/ctx  (.getContext (dom/getElement "canvas") "2d")
         :canvas/background-color "white" ; default canvas color (background)
         :canvas/width  640
         :canvas/height 640
         ;; division of canvas
         :canvas-unit/width  32                 ; :canvas/width / 20
         :canvas-unit/height 32 ;               ; :canvas/height / 20
         ;; snake object
         :snake/body '([9 9] [8 9] [7 9]) ; [x y]
         :snake/direction [0 1]          ; default direction, see `keycode->direction`
         :snake/border 2                  ; border size
         :snake/body-color "lime"         ; snake's body color
         :snake/food nil                  ; when `nil`, regenerate it
         :snake/food-color "red"          ; the color of food
         :snake/alive true                ; when `false`, stop game loop
         }))

(defn print-states
  "Print current game states on console.(For debugging purpose)"
  []
  (.log js/console "-------------------------------")
  (.log js/console (with-out-str (pprint @states)))
  (.log js/console "-------------------------------\n"))

(defn axis-add
  [[x1 y1] [x2 y2]]
  [(+ x1 x2) (+ y1 y2)])

(defn axis-equal?
  [x y]
  (= x y))

(defn draw-canvas-unit
  "Draw a unit on canvas"
  [[x y] color]
  (let [{:keys [:canvas/ctx :canvas-unit/width :canvas-unit/height]} @states]
    (aset ctx "fillStyle" color)
    (.fillRect ctx
               (* x width)
               (* y height)
               width
               height)))

(defn resize-canvas
  "Resize the canvas according to the states"
  []
  (let [{:keys [:canvas/element :canvas/width :canvas/height]} @states]
    (.setAttribute element "width" width)
    (.setAttribute element "height" height)))

(defn keycode->direction
  "Convert JavaScript keycode to direction array"
  [keycode]
  (get {goog.events.KeyCodes.J [0 -1] ;up
        goog.events.KeyCodes.K [0 1] ;down
        goog.events.KeyCodes.H [-1 0] ;left
        goog.events.KeyCodes.L [1 0]} ;right
       keycode nil))

(defn opposite-direction?
  [dir1 dir2]
  (= [0 0] (axis-add dir1 dir2)))

(defn on-keydown
  [event]
  (let [{:keys [:snake/direction]} @states
        new-direction (keycode->direction (.-keyCode event))]
    (println "EVENT: " (.-keyCode event))
    (println "DIRECTION / NEW" direction new-direction)
    (when (and new-direction (not (opposite-direction? direction new-direction)))
      (swap! states assoc-in [:snake/direction] new-direction))))

(defn out-of-boundary?
  "Check if axis is exceed the game board boundary."
  [[x y]]
  (let [max-x (/ (:canvas/width @states) (:canvas-unit/width @states))
        max-y (/ (:canvas/height @states) (:canvas-unit/height @states))]
    (or (>= x max-x) (< x 0) (>= y max-y) (< y 0))))

(defn snake-collision?
  [[x y]]
  (let [{:keys [:snake/body]} @states]
    (some #(axis-equal? [x y] %) body)))

(defn eat-food?
  [[x y]]
  (let [{:keys [:snake/food]} @states]
    (= [x y] food)))

(defn generate-food
  []
  (let [{:keys [:snake/food :snake/food-color]} @states
        max-x (/ (:canvas/width @states) (:canvas-unit/width @states))
        max-y (/ (:canvas/height @states) (:canvas-unit/height @states))]
    (when (nil? food)
      (loop [food [(rand-int max-x) (rand-int max-y)]]
        (if-not (snake-collision? food)
          (do
            (swap! states assoc-in [:snake/food] food)
            (draw-canvas-unit food food-color)
            food)
          (recur ([(rand-int max-x) (rand-int max-y)])))))))

(defn game-loop
  []
  (let [{:keys [:game/speed :canvas/background-color :snake/body :snake/body-color :snake/direction]} @states
        head (axis-add (first body) direction)
        tail (last body)]
    (generate-food)
    (when (snake-collision? head)
      (js/alert "You're DEAD!")
      (swap! states assoc-in [:snake/alive] false))
    (when (out-of-boundary? head)
      (js/alert "Snake is out of boundery at :" head)
      (swap! states assoc-in [:snake/alive] false))
    (when (:snake/alive @states)
      (draw-canvas-unit head body-color)
      (if (eat-food? head)
        (swap! states assoc
               :snake/food nil
               :snake/body (conj body head))
        (do
          (draw-canvas-unit tail background-color)
          (swap! states assoc-in [:snake/body] (-> (conj body head) drop-last))))
      (js/window.setTimeout (fn [] (game-loop)) speed))))

(defn init
  []
  (resize-canvas)
  (events/removeAll js/document)
  (events/listen js/document goog.events.EventType.KEYDOWN on-keydown)
  (game-loop))

; (resize-canvas)
; (draw-canvas-unit [0 0] "red")
; (draw-canvas-unit [10 10] "blue")
; (draw-canvas-unit [19 19] "green")
(init)
