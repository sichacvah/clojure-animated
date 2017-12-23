(ns clojure-animated.decay
  (:require [clojure-animated.protocols :as protocols :refer [IAnimated start! is-done? animate update!]]
            [cljs.core.async :as async :refer [<! >!]]
            [clojure-animated.utils :as utils])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(def config
  {:start         0
   :from          0
   :velocity      1
   :deceleration  0.997
   :interpolation identity
   :type          :decay})

(deftype Decay [*value config stops-ch ticks-ch]
  IAnimated
  (is-done? [this [time]]
    (let [{:keys [velocity deceleration]} config]
      (>= 0 (- velocity (/ (* deceleration time) 1000)))))
  (animate  [this [time]]
      (let [{:keys [from velocity deceleration interpolation duration]} config
            s (- (* velocity time) (/ (* deceleration time time) 2 1000 1000))]
         (+ from (interpolation s))))
  (update!  [this value]
    (let [update! (:update! config)]
      (if (some? update!) (update! value) (reset! *value value))))
  (start!   [this] (utils/perform-start! this stops-ch ticks-ch))
  (stop!    [this] (async/close! stops-ch)))

(defn decay [*value config']
  (let [stops (async/chan)
        ticks (async/chan)]
    (Decay. *value (merge config config') stops ticks)))
