(ns clojure-animated.timing
  (:require [clojure-animated.protocols :as protocols :refer [IAnimated start! is-done? animate update!]]
            [cljs.core.async :as async :refer [<! >!]]
            [clojure-animated.utils :as utils])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))


(def pi (.-PI js/Math))
(def cos js/Math.cos)

(defn default-ease [x]
  (/ (- 1 (js/Math.cos (* pi x))) 2))

(defn clamp [from to num]
  (cond
    (< num from) from
    (>= num to)  to
    :else        num))

(def config
  {:start          0
   :from           0
   :to             1
   :ease           default-ease
   :interpolation identity
   :duration       750
   :delay          0
   :type           :timing})

(deftype Timing [*value config stops-ch ticks-ch]
  IAnimated
  (is-done? [this [time]]
    (let [{:keys [start delay from to duration]} config]
      (>= time (+ start delay duration))))
  (animate  [this [time]]
      (let [{:keys [start delay speed ramp from to ease interpolation duration]} config
            fr (clamp 0 1 (/ (- time start delay) duration))
            eased (interpolation (ease fr))]
       (if (is-done? this [time])
          to
          (+ from (* (- to from) eased)))))
  (update!  [this value] 
    (let [update! (:update! config)]
      (if (some? update!) (update! value) (reset! *value value))))
  (start!   [this] (utils/perform-start! this stops-ch ticks-ch))
  (stop!    [this] (async/close! stops-ch)))


(defn timing [*value config']
  (let [stops (async/chan)
        ticks (async/chan)]
      (Timing. *value (merge config config') stops ticks)))
