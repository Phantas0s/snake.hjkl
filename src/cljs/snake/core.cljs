(ns snake.core
  (:require [goog.dom :as dom]
            [goog.events :as events]
            [goog.events.KeyCodes]
            [goog.events.EventType]
            [snake.levels :refer [levels]]
            [clojure.pprint :refer [pprint]]))

(enable-console-print!)

; TODO - Bring back as much changing state in game loop and see what to do with them
; TODO - Implement watcher for draw-level / points?
; TODO - See how to use core.async to pass new data from handlers and reduce states

;; ------------------------------
;; States

; TODO refactor all this messy states. Make immutable what we can.

(def canvas {:width 640
             :height 640
             :background-color "#1c1c1c"
             :element (dom/getElement "canvas")
             :ctx (.getContext (dom/getElement "canvas") "2d")
             :unit/width 32 ;(/ (:width canvas) 20)
             :unit/height 32 ;(/ (:height canvas) 20)
             :max-x 20 ;(/ (:canvas-width canvas) (:unit/width canvas))
             :max-y 20}) ;(/ (:canvas-height canvas) (:unit/height canvas))})

(def message-box-element (.getElementById js/document "message-box"))
(def hud-score-element (.getElementById js/document "score"))
(def hud-level-element (.getElementById js/document "level"))

(def next-level-score 10)
(def food-points 10)
(def food-color "#949494")
(def snake-color "#d0d0d0")
(def game-speed 200)

(def states
  (atom {:game/key-lock false
         :snake/food nil; when `nil`, regenerate it
         :wall/color "black"}))

(def snake-defaults {:snake/body '([5 9] [4 9] [3 9])
                     :snake/direction [1 0]
                     :snake/food nil
                     :snake/alive true
                     :snake/direction-queue []})

(def defaults (merge {:game/level 1
                      :game/pause true
                      :game/score 0} snake-defaults))

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
  (let [{:keys [:ctx :unit/width :unit/height]} canvas]
    (aset ctx "fillStyle" color)
    (.fillRect ctx
               (* x width)
               (* y height)
               width
               height)))

(defn draw-circle
  "Draw a circle on canvas"
  [[x y] color]
  (let [{:keys [:ctx :unit/width :unit/height]} canvas]
    (aset ctx "fillStyle" color)
    (.beginPath ctx)
    (.arc ctx
          (+ (/ width 2) (* x width))
          (+ (/ height 2) (* y height))
          (/ height 2)
          0
          (* 2 Math/PI)
          false)
    (.fill ctx)))

(defn update-text!
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
(get-walls 1)

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
   (update-text! text (.getElementById js/document "message"))))

(defn resize-canvas
  "Resize the canvas according to the states"
  []
  (let [{:keys [:element :width :height]} canvas]
    (.setAttribute element "width" width)
    (.setAttribute element "height" height)))

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
  [[x y]]
  (let [{:keys [:snake/body]} @states]
    (some #(axis-equal? [x y] %) body)))

(defn wall-collision?
  [coord]
  (let [{:keys [:game/level]} @states
        walls (get-walls level)]
    (some #(= coord %) walls)))

;; ------------------------------
;; Keys Mechanics

(defn keycode->direction
  "Convert JavaScript keycode to direction array"
  [keycode]
  (get {goog.events.KeyCodes.K [0 -1] ;up
        goog.events.KeyCodes.J [0 1] ;down
        goog.events.KeyCodes.H [-1 0] ;left
        goog.events.KeyCodes.L [1 0]} ;right
       keycode nil))

(defn release-key-lock!
  "Fix a bug when pressing keys simultaneously"
  []
  (swap! states assoc-in [:game/key-lock] false))

(defn set-key-lock!
  "Fix a bug when pressing keys simultaneously"
  []
  (swap! states assoc-in [:game/key-lock] true))

(defn key-lock?
  []
  (:game/key-lock @states))

;; ------------------------------
;; Game Mechanics

(defn opposite-direction?
  [dir1 dir2]
  (= [0 0] (axis-add dir1 dir2)))

(defn out-of-boundary?
  "Check if axis is exceed the game board boundary."
  [[x y]]
  (let [{:keys [:max-x :max-y]} canvas]
    (or (>= x max-x) (< x 0) (>= y max-y) (< y 0))))

(defn eat-food?
  [[x y]]
  (let [{:keys [:snake/food]} @states]
    (= [x y] food)))

(defn generate-food
  []
  (let [{:keys [:max-x :max-y]} canvas]
    (loop [rand-x (rand-int max-x)
           rand-y (rand-int max-y)]
      (let [food [rand-x rand-y]]
        (if-not (or
                 (snake-collision? food)
                 (wall-collision? food))
          food
          (recur (rand-int max-x) (rand-int max-y)))))))

(defn lose?
  [snake-head]
  (or
   (snake-collision? snake-head)
   (out-of-boundary? snake-head)
   (wall-collision? snake-head)))

(defn canvas-reset
  []
  (let [{:keys [:background-color :ctx :width :height]} canvas]
    (aset ctx "fillStyle" background-color)
    (.fillRect ctx
               0
               0
               width
               height)))

(defn level-reset
  []
  (canvas-reset)
  (let [food (generate-food)
        {:keys [:game/level]} @states]
    (swap! states merge snake-defaults)
    (swap! states assoc-in [:snake/food] food)
    (draw-circle food food-color)
    (draw-wall-level level)
    (message-box-show (str "Level " level))))

(defn game-reset
  []
  (swap! states merge defaults)
  (update-text! (:game/level @states) hud-level-element)
  ; (message-box-hide)
  (level-reset))

(defn next-level?
  []
  (let [{:keys [:game/score :game/level]} @states]
    (>= score (* level next-level-score))))

(defn game-loop
  [tframe last-time]
  (let [{:keys [:game/score
                :snake/body
                :snake/direction
                :snake/direction-queue]} @states
        head (axis-add (first body) direction)
        tail (last body)]
    (update-text! score hud-score-element)
    (release-key-lock!)
    (when (next-level?)
      (swap! states update-in [:game/level] inc)
      (swap! states assoc-in [:game/pause] true)
      (swap! states merge snake-defaults)
      (update-text! (:game/level @states) hud-level-element)
      (level-reset))

    (when (lose? head)
      (message-box-show "You're DEAD!!!")
      (game-reset))

    (when-not (:game/pause @states))
    (if (>= tframe (+ last-time game-speed))
      (do
        (let [next-snake-direction (first direction-queue)]
          (when-not (or (empty? direction-queue) (opposite-direction? next-snake-direction direction))
            (swap! states assoc
                   :snake/direction next-snake-direction
                   :snake/direction-queue (drop 1 (:snake/direction-queue @states)))))

        (draw-rect head snake-color)
        (if (eat-food? head)
          (let [food (generate-food)]
            (do
              (swap! states assoc-in [:snake/body] (conj body head))
              (swap! states update-in [:game/score] + food-points)
              (swap! states assoc-in [:snake/food] food)
              (draw-circle food food-color)))
          (do
            (draw-rect tail (:background-color canvas))
            (swap! states assoc-in [:snake/body] (drop-last (conj body head)))))
        (js/window.requestAnimationFrame (fn [tframe] (game-loop tframe (js/window.performance.now)))))
      (js/window.requestAnimationFrame (fn [tframe] (game-loop tframe last-time))))))

(defn on-retry
  [event]
  (do
    (message-box-hide)
    (swap! states assoc-in [:game/pause] false)))

(defn on-keydown
  [event]
  (when (= (.-keyCode event) goog.events.KeyCodes.ENTER)
    (on-retry event))
  (let [{:keys [:snake/direction]} @states
        new-direction (keycode->direction (.-keyCode event))]
    (if (key-lock?)
      (swap! states assoc-in [:snake/direction-queue] (conj (:game/key-queue @states) new-direction))
      (when (and new-direction (not (opposite-direction? direction new-direction)))
        (swap! states assoc-in [:snake/direction] new-direction)
        (set-key-lock!)))))

(defn init
  []
  (resize-canvas)
  (canvas-reset)
  (events/removeAll js/document)
  (events/listen js/document goog.events.EventType.KEYDOWN on-keydown)
  (events/listen message-box-element goog.events.EventType.CLICK on-retry)
  (game-reset)
  (game-loop (js/window.performance.now) 0))

(init)
