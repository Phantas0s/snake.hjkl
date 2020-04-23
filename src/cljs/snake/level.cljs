(ns snake.level
  (:require [goog.dom :as dom]
            [goog.dom.classlist :as classlist]
            [goog.events :as events]
            [cljs.reader :as reader]
            [goog.events.EventType]
            [clojure.pprint :refer [pprint]]))

(def level (atom []))

(enable-console-print!)

(def board (.getElementById js/document "board"))
(def coord (.getElementById js/document "coord"))
(def reset-button (.getElementById js/document "reset"))

(defn create-unit
  [coord]
  (str "
       <div class='unit' id='" coord "'>
                                </div>
                                "))
(defn create-board-elements
  [y x-total y-total]
  (loop [next-y y
         result []]
    (if (>= next-y y-total)
      result
      (recur (inc next-y) (into result (map create-unit (map vector (range 0 x-total) (repeat x-total next-y))))))))

(set! (.-innerHTML board) (clojure.string/join (create-board-elements 0 20 20)))

(defn has-level?
  [lvl]
  (filter #(= lvl %) @level))

(defn write-coord
  []
  (set! (.-innerHTML coord) (str @level)))

(def white-class "white")
(defn on-select-unit
  [event]
  (let [el (.-target event)
        lvl (reader/read-string (.-id el))]
    (classlist/toggle el white-class)
    (if (or (empty? @level) (empty? (has-level? lvl)))
      (swap! level into (vector lvl))
      (let [new-levels (filter #(not= lvl %) @level)]
        (swap! level empty)
        (swap! level into new-levels))))
  (write-coord))

(defn on-reset
  []
  (println "hello")
  (doall (map #(classlist/toggle % white-class) (array-seq (dom/getElementsByClass white-class))))
  (swap! level empty)
  (write-coord))

(events/listen board goog.events.EventType.CLICK on-select-unit)
(events/listen reset-button goog.events.EventType.CLICK on-reset)
