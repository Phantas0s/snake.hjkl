(ns snake.level-editor
  (:require [goog.dom :as dom]
            [goog.dom.classlist :as classlist]
            [goog.events :as events]
            [cljs.reader :as reader]
            [goog.events.EventType]
            [clojure.pprint :refer [pprint]]))

(enable-console-print!)

(def board (.getElementById js/document "board"))
(def coord (.getElementById js/document "coord"))
(def reset-button (.getElementById js/document "reset"))
(def refresh-button (.getElementById js/document "refresh"))

(def walls (atom []))

(def snake-body [[5 9] [4 9] [3 9]])
(defn create-unit
  [coord]
  (if (some #(= coord %) snake-body)
    (str "<div class='snake'></div>")
    (str "<div class='unit' id='" coord "'></div>")))

(defn create-board-elements
  [y x-total y-total]
  (loop [next-y y
         result []]
    (if (>= next-y y-total)
      result
      (recur (inc next-y) (into result (map create-unit (map vector (range 0 x-total) (repeat x-total next-y))))))))

(set! (.-innerHTML board) (clojure.string/join (create-board-elements 0 20 20)))

(defn has-walls?
  [lvl]
  (filter #(= lvl %) @walls))

(defn write-coord
  []
  (set! (.-value coord) (str @walls)))

(defn replace-walls
  "Replace every walls of the level with new walls"
  [new-walls]
  (swap! walls empty)
  (swap! walls into new-walls))

(def white-class "white")
(defn read-coord
  "Read the walls coordinates from the coordinates textarea"
  []
  (let [new-walls (reader/read-string (.-value coord))]
    (doseq [walls new-walls] (classlist/toggle (.getElementById js/document (str walls)) white-class))
    new-walls))

(defn on-select-unit
  "When clicking on the board to create a wall"
  [event]
  (let [el (.-target event)
        lvl (reader/read-string (.-id el))]
    (classlist/toggle el white-class)
    (if (or (empty? @walls) (empty? (has-walls? lvl)))
      (swap! walls into (vector lvl))
      (let [new-walls (filter #(not= lvl %) @walls)]
        (replace-walls new-walls))))
  (write-coord))

(defn reset-board
  []
  (doall (map #(classlist/toggle % white-class) (array-seq (dom/getElementsByClass white-class))))
  (swap! walls empty))

(defn on-reset
  "Delete every walls on the board"
  []
  (reset-board)
  (write-coord))

(defn on-refresh
  "Add the walls on the board from the coordinates textarea"
  []
  (reset-board)
  (replace-walls (read-coord)))

(events/listen board goog.events.EventType.CLICK on-select-unit)
(events/listen reset-button goog.events.EventType.CLICK on-reset)
(events/listen refresh-button goog.events.EventType.CLICK on-refresh)
