(ns snake.core
  (:require [goog.dom :as dom]
            [goog.events :as events]
            [goog.events.KeyCodes]
            [goog.events.EventType]
            [clojure.pprint :refer [pprint]]))

(enable-console-print!)

;; ------------------------------
;; States

; TODO - BUG: death when J L quickly, going to the left
; TODO - BUG: food can appear in walls

(def states
  (atom {;; canvas object
         :game/speed 150
         :game/level 1 ;set by level-reset
         :game/score nil

         :canvas/element  (dom/getElement "canvas")
         :canvas/ctx  (.getContext (dom/getElement "canvas") "2d")
         :canvas/background-color "#1c1c1c" ; default canvas color (background)
         :canvas/width  640
         :canvas/height 640
         ;; division of canvas
         :canvas-unit/width  32                 ; :canvas/width / 20
         :canvas-unit/height 32 ;               ; :canvas/height / 20
         ;; snake object
         :snake/body nil ;set by level-reset
         :snake/direction nil ;set by level-reset
         :snake/border 2                  ; border size
         :snake/food-color "#949494"          ; the color of food
         :snake/body-color "#d0d0d0"         ; snake's body color
         :snake/food nil                  ; when `nil`, regenerate it
         :snake/alive true                ; when `false`, stop game loop
         :food/points 10
         :wall/color "black"}))

; WALLS

(defn fill-wall-y
  [[begin-y end-y] x]
  (map #(vector x  %) (range begin-y (inc end-y))))

(defn fill-wall-x
  [[begin-x end-x] y]
  (map #(vector % y) (range begin-x (inc end-x))))

(def levels {:level-3 {:walls [[9 4] [9 5]
                               [9 11] [9 12]]}
             :level-2 {:walls
                       (into [] (concat (fill-wall-y [4 7] 9)
                                        (fill-wall-y [11 14] 9)))}
             :level-1 {:walls
                       (into [] (concat (fill-wall-x [6 13] 0)
                                        (fill-wall-x [7 12] 1)
                                        (fill-wall-x [8 11] 2)
                                        (fill-wall-x [8 11] 3)
                                        (fill-wall-x [8 11] 16)
                                        (fill-wall-x [8 11] 17)
                                        (fill-wall-x [7 12] 18)
                                        (fill-wall-x [6 13] 19)))}})

(defn print-states
  "Print current game states on console.(For debugging purpose)"
  []
  (.log js/console "-------------------------------")
  (.log js/console (with-out-str (pprint @states)))
  (.log js/console "-------------------------------\n"))

;; ------------------------------
;; Reset functions

(defn canvas-reset
  []
  (let [{:keys [:canvas/ctx :canvas/width :canvas/height :canvas/background-color]} @states]
    (aset ctx "fillStyle" background-color)
    (.fillRect ctx
               0
               0
               width
               height)))

(defn level-reset
  []
  (canvas-reset)
  (swap! states assoc
         :snake/body '([5 9] [4 9] [3 9])
         :snake/direction [1 0]
         :snake/food nil)
  (js/alert (str "Level " (:game/level @states) " - Are you READY?")))

(defn score-reset
  []
  (swap! states assoc-in [:game/score] 0))

(defn game-reset
  []
  (swap! states assoc
         :game/level 1
         :snake/alive true)
  (score-reset)
  (level-reset))

(defn axis-add
  [[x1 y1] [x2 y2]]
  [(+ x1 x2) (+ y1 y2)])

(defn axis-equal?
  [x y]
  (= x y))

;; ------------------------------
;; Draw functions

(defn draw-rect
  "Draw a rect on canvas"
  [[x y] color]
  (let [{:keys [:canvas/ctx :canvas-unit/width :canvas-unit/height]} @states]
    (aset ctx "fillStyle" color)
    (.fillRect ctx
               (* x width)
               (* y height)
               width
               height)))

(defn draw-circle
  "Draw a circle on canvas"
  [[x y] color]
  (let [{:keys [:canvas/ctx :canvas-unit/width :canvas-unit/height]} @states]
    (aset ctx "fillStyle" color)
    (.beginPath ctx)
    (.arc ctx
          (+ (/ width 2) (* x width))
          (+ (/ height 2) (* y height))
          (/ width 2)
          0
          (* 2 Math/PI)
          false)
    (.fill ctx)))

(defn draw-text
  "Draw some text"
  [id text]
  (set! (.-innerHTML (.getElementById js/document id)) text))

(defn draw-wall
  [wall-units color]
  (when-not (nil? wall-units)
    (doseq [w wall-units]
      (draw-rect w color))))

(defn get-walls
  [level]
  (get-in levels [(keyword (str "level-" level)) :walls]))

(defn draw-wall-level
  [level]
  (draw-wall (get-walls level) (:wall/color @states)))

;; ------------------------------
;;


(defn resize-canvas
  "Resize the canvas according to the states"
  []
  (let [{:keys [:canvas/element :canvas/width :canvas/height]} @states]
    (.setAttribute element "width" width)
    (.setAttribute element "height" height)))

(defn keycode->direction
  "Convert JavaScript keycode to direction array"
  [keycode]
  (get {goog.events.KeyCodes.K [0 -1] ;up
        goog.events.KeyCodes.J [0 1] ;down
        goog.events.KeyCodes.H [-1 0] ;left
        goog.events.KeyCodes.L [1 0]} ;right
       keycode nil))

(defn opposite-direction?
  [dir1 dir2]
  (= [0 0] (axis-add dir1 dir2)))

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

(defn wall-collision?
  [coordinates]
  (let [{:keys [:game/level]} @states
        walls (get-walls level)]
    (some #(= coordinates %) walls)))

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
        (if-not (or
                 (snake-collision? food)
                 (wall-collision? food))
          (do
            (swap! states assoc-in [:snake/food] food)
            (draw-circle food food-color)
            food)
          (recur ([(rand-int max-x) (rand-int max-y)])))))))

(defn snake-dead?
  [snake-head]
  (or
   (snake-collision? snake-head)
   (out-of-boundary? snake-head)
   (wall-collision? snake-head)))

(defn add-points
  [points]
  (swap! states update-in [:game/score] + points))

; TODO to improve that - might use a watch?
(defn next-level
  [score]
  (let [{:keys [:game/level]} @states]
    (when (>= score (* level 100))
      (swap! states update-in [:game/level] + 1)
      (level-reset)
      (draw-wall-level (+ level 1)))))

(defn game-loop
  []
  (let [{:keys [:game/speed
                :game/score
                :food/points
                :canvas/background-color
                :snake/body
                :snake/body-color
                :snake/direction]} @states
        head (axis-add (first body) direction)
        tail (last body)]
    (draw-text "score" score)
    (generate-food)
    (if (snake-dead? head)
      (do
        (js/alert "You're DEAD!!!")
        (swap! states assoc-in [:snake/alive] false)))
    (when (:snake/alive @states)
      (draw-rect head body-color)
      (if (eat-food? head)
        (do
          (swap! states assoc
                 :snake/food nil
                 :snake/body (conj body head))
          (add-points points))
        (do
          (draw-rect tail background-color)
          (swap! states assoc-in [:snake/body] (-> (conj body head) drop-last))))
      (next-level score)
      (js/window.setTimeout (fn [] (game-loop)) speed))))

; TODO add "press enter" to restart the game
(defn on-keydown
  [event]
  (let [{:keys [:snake/direction]} @states
        new-direction (keycode->direction (.-keyCode event))]
    (when (and new-direction (not (opposite-direction? direction new-direction)))
      (swap! states assoc-in [:snake/direction] new-direction))))

(defn init
  []
  (game-reset)
  (let [{:keys [:game/level]} @states]
    (resize-canvas)
    (events/removeAll js/document)
    (events/listen js/document goog.events.EventType.KEYDOWN on-keydown)
    (draw-wall-level level)
    (game-loop)))

; (resize-canvas)
; (draw-rect [0 0] "red")
; (draw-rect [10 10] "blue")
; (draw-rect [19 19] "green")
(init)

(defn on-retry
  [event]
  (when (= false (:snake/alive @states))
    (init)))

(events/listen (.getElementById js/document "main") goog.events.EventType.CLICK on-retry)
