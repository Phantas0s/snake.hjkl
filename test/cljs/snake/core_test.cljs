(ns snake.core-test
  (:require [snake.core :as core]
            [cljs.test :refer-macros [deftest is testing run-tests]]))

(deftest test-numbers
  (is (= 1 1)))
