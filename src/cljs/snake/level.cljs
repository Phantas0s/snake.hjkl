(ns snake.level
  (:require [goog.dom :as dom]
            [goog.dom.classlist :as gc]
            [goog.events :as events]
            [goog.events.KeyCodes]
            [cljs.reader :as reader]
            [goog.events.EventType]
            [clojure.pprint :refer [pprint]]))

(def level (atom []))

(enable-console-print!)

(def board (.getElementById js/document "board"))
(def coord (.getElementById js/document "coord"))

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

(defn on-select-unit
  [event]
  (let [el (.-target event)
        lvl (reader/read-string (.-id el))]
    (gc/toggle el "white")
    (println lvl)
    (if (empty? (has-level? lvl))
      (swap! level conj lvl)
      (do (swap! level empty)
          (swap! level into (filter #(not (= % lvl)) @level)))
    (println "total: " @level)))

(defn on-select-test
  [])
(events/listen board goog.events.EventType.CLICK on-select-unit)

