(ns snake.core
  (:require [goog.dom :as dom]
            [goog.events :as events]
            [goog.events.KeyCodes]
            [goog.events.EventType]
            [clojure.pprint :refer [pprint]]))

(enable-console-print!)

; TODO - BUG: death when J L quickly, going to the left
; TODO - Implement watcher for draw-level / points

;; ------------------------------
;; States

(def canvas-width 640)
(def canvas-height 640)
(def canvas-background-color "#1c1c1c")
(def canvas-element (dom/getElement "canvas"))
(def canvas-ctx (.getContext (dom/getElement "canvas") "2d"))

; canvas atomic units
(def canvas-unit-width (/ canvas-width 20))
(def canvas-unit-height (/ canvas-height 20))

(def canvas-max-x (/ canvas-width canvas-unit-width))
(def canvas-max-y (/ canvas-height canvas-unit-height))

(def message-box-element (.getElementById js/document "message-box"))
(def hud-score-element (.getElementById js/document "score"))
(def hud-level-element (.getElementById js/document "level"))

(def states
  (atom {;; canvas object
         :game/speed 150
         :game/level 1 ;set by level-reset
         :game/score 0 ;set by reset-level
         :game/pause true ; when true stop game loop
         ;; snake object
         :snake/body nil ;set by level-reset
         :snake/direction nil ;set by level-reset
         :snake/food-color "#949494"
         :snake/body-color "#d0d0d0"
         :snake/food nil                  ; when `nil`, regenerate it
         :snake/alive true                ; when `false`, stop game loop and reset game
         :food/points 10
         :wall/color "black"}))

; WALLS

(defn fill-wall-y
  [[begin-y end-y] x]
  (map #(vector x  %) (range begin-y (inc end-y))))

(defn fill-wall-x
  [[begin-x end-x] y]
  (map #(vector % y) (range begin-x (inc end-x))))

(def levels {:level-1 {:walls [[9 4] [9 5]
                               [9 11] [9 12]]}
             :level-2 {:walls
                       (into [] (concat (fill-wall-y [4 7] 9)
                                        (fill-wall-y [11 14] 9)))}
             :level-3 {:walls
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
;; Draw functions

(defn draw-rect
  "Draw a rect on canvas"
  [[x y] color]
  (aset canvas-ctx "fillStyle" color)
  (.fillRect canvas-ctx
             (* x canvas-unit-width)
             (* y canvas-unit-height)
             canvas-unit-width
             canvas-unit-height))

(defn draw-circle
  "Draw a circle on canvas"
  [[x y] color]
  (aset canvas-ctx "fillStyle" color)
  (.beginPath canvas-ctx)
  (.arc canvas-ctx
        (+ (/ canvas-unit-width 2) (* x canvas-unit-width))
        (+ (/ canvas-unit-height 2) (* y canvas-unit-height))
        (/ canvas-unit-width 2)
        0
        (* 2 Math/PI)
        false)
  (.fill canvas-ctx))

(defn draw-text
  "Draw some text"
  [text element]
  (set! (.-innerHTML element) text))

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
;; Reset functions


(defn message-box-hide
  []
  (set! (.-display (.-style message-box-element)) "none"))

(defn message-box-show
  ([]
   (set! (.-display (.-style message-box-element)) "block"))
  ([text]
   (set! (.-display (.-style message-box-element)) "block")
   (draw-text text (.getElementById js/document "message"))))

(defn resize-canvas
  "Resize the canvas according to the states"
  []
  (.setAttribute canvas-element "width" canvas-width)
  (.setAttribute canvas-element "height" canvas-height))

(defn canvas-reset
  []
  (aset canvas-ctx "fillStyle" canvas-background-color)
  (.fillRect canvas-ctx
             0
             0
             canvas-width
             canvas-height))

(defn level-reset
  []
  (canvas-reset)
  (swap! states assoc
         :snake/body '([5 9] [4 9] [3 9])
         :snake/direction [1 0]
         :snake/food nil)
  (draw-wall-level (:game/level @states))
  (message-box-show (str "Level " (:game/level @states))))

(defn score-reset
  []
  (swap! states assoc-in [:game/score] 0))

(defn game-reset
  []
  (swap! states assoc
         :game/level 1
         :game/pause true
         :snake/alive true)
  (draw-text (:game/level @states) hud-level-element)
  (score-reset)
  (message-box-hide)
  (level-reset))

(defn axis-add
  [[x1 y1] [x2 y2]]
  [(+ x1 x2) (+ y1 y2)])

(defn axis-equal?
  [x y]
  (= x y))

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
  (or (>= x canvas-max-x) (< x 0) (>= y canvas-max-y) (< y 0)))

(defn snake-collision?
  [[x y]]
  (let [{:keys [:snake/body]} @states]
    (some #(axis-equal? [x y] %) body)))

(defn wall-collision?
  [coord]
  (let [{:keys [:game/level]} @states
        walls (get-walls level)]
    (some #(= coord %) walls)))

(defn eat-food?
  [[x y]]
  (let [{:keys [:snake/food]} @states]
    (= [x y] food)))

(defn generate-food
  []
  (let [{:keys [:snake/food :snake/food-color]} @states]
    (when (nil? food)
      (loop [rand-x (rand-int canvas-max-x)
             rand-y (rand-int canvas-max-y)]
        (let [food [rand-x rand-y]]
          (if-not (or
                   (snake-collision? food)
                   (wall-collision? food))
            (do
              (swap! states assoc-in [:snake/food] food)
              (draw-circle food food-color)
              food)
            (recur (rand-int canvas-max-x) (rand-int canvas-max-y))))))))

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
      (swap! states update-in [:game/level] inc)
      (swap! states assoc-in [:game/pause] true)
      (level-reset)
      (draw-text (:game/level @states) hud-level-element))))

(defn game-loop
  []
  (let [{:keys [:game/speed
                :game/score
                :food/points

                :snake/body
                :snake/body-color
                :snake/direction]} @states
        head (axis-add (first body) direction)
        tail (last body)]
    (draw-text score hud-score-element)
    (generate-food)
    (if (snake-dead? head)
      (do
        (message-box-show "You're DEAD!!!")
        (swap! states assoc-in [:snake/alive] false)))
    (when (and (:snake/alive @states) (not (:game/pause @states)))
      (draw-rect head body-color)
      (if (eat-food? head)
        (do
          (swap! states assoc
                 :snake/food nil
                 :snake/body (conj body head))
          (add-points points))
        (do
          (draw-rect tail canvas-background-color)
          (swap! states assoc-in [:snake/body] (-> (conj body head) drop-last))))
      (next-level score)
      (js/window.setTimeout (fn [] (game-loop)) speed))))

(defn on-retry
  [event]
  (cond
    (= false (:snake/alive @states)) (game-reset)
    (= true (:game/pause @states)) (do
                                     (message-box-hide)
                                     (swap! states assoc-in [:game/pause] false)
                                     (game-loop))))

; TODO add "press enter" to restart the game
(defn on-keydown
  [event]
  (when (= (.-keyCode event) goog.events.KeyCodes.ENTER)
    (on-retry event))
  (let [{:keys [:snake/direction]} @states
        new-direction (keycode->direction (.-keyCode event))]
    (when (and new-direction (not (opposite-direction? direction new-direction)))
      (swap! states assoc-in [:snake/direction] new-direction))))

(defn init
  []
  (let [{:keys [:game/level]} @states]
    (resize-canvas)
    (canvas-reset)
    (game-reset)
    (events/removeAll js/document)
    (events/listen js/document goog.events.EventType.KEYDOWN on-keydown)

    (events/listen (.getElementById js/document "message-box") goog.events.EventType.CLICK on-retry)
    (game-loop)))

(init)
