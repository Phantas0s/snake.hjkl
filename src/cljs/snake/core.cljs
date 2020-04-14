(ns snake.core
  (:require [goog.dom :as dom]
            [goog.events :as events]
            [goog.events.KeyCodes]
            [goog.events.EventType]
            [clojure.pprint :refer [pprint]]))

(enable-console-print!)

(def state
  (atom {;; canvas object
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
         :snake/body '([7 9] [8 9] [9 9]) ; [x y]
         :snake/direction [-1 0]          ; default direction, see `keycode->direction`
         :snake/border 2                  ; border size
         :snake/body-color "lime"         ; snake's body color
         :snake/food nil                  ; when `nil`, regenerate it
         :snake/food-color "red"          ; the color of food
         :snake/alive true                ; when `false`, stop game loop
         }))

(defn print-state
  "Print current game state on console.(For debugging purpose)"
  []
  (.log js/console "-------------------------------")
  (.log js/console (with-out-str (pprint @state)))
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
  (let [{:keys [:canvas/ctx :canvas-unit/width :canvas-unit/height]} @state]
    (aset ctx "fillStyle" color)
    (.fillRect ctx
               (* x width)
               (* y height)
               width
               height)))

(defn resize-canvas
  "Resize the canvas according to the states"
  []
  (let [{:keys [:canvas/element :canvas/width :canvas/height]} @state]
    (.setAttribute element "width" width)
    (.setAttribute element "height" height)))

(defn keycode->direction
  "Convert JavaScript keycode to direction array"
  [keycode]
  (get {goog.events.KeyCodes.K [0 -1] ;up
        goog.events.KeyCodes.J [0 1] ;down
        goog.events.KeyCodes.H [-1 0] ;left
        goog.events.KeyCodes.L [1 0]} ;right
       keycode))

(defn opposite-direction?
  [dir1 dir2]
  (= [0 0] (axis-add dir1 dir2)))

(defn on-keydown
  [event]
  (let [{:keys [:snake/direction]} @state
        new-direction (keycode->direction (.-keycode event))]
    (when (and new-direction (not (opposite-direction? direction new-direction)))
      (swap! state assoc-in [:snake/direction] new-direction))))

(defn out-of-boundary?
  "Check if axis is exceed the game board boundary."
  [[x y]]
  (let [max-x (/ (:canvas-width @state) (:canvas-unit/width @state))
        max-y (/ (:canvas-height @state) (:canvas-unit/height @state))]
    (or (>= x max-x) (< x 0) (>= y max-y) (< y 0))))

(defn snake-collision?
  [[x y]]
  (let [{:keys [:snake/body]} @state]
    (some #(axis-equal? [x y] %) body)))

(defn eat-food?
  [[x y]]
  (let [{:keys [:snake/food]} @state]
    (= [x y] food)))

(resize-canvas)
(draw-canvas-unit [0 0] "red")
(draw-canvas-unit [10 10] "blue")
(draw-canvas-unit [19 19] "green")
