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
  (config [_] config)
  (is-done? [this state]
    (let [{:keys [velocity time]} state]
      (>= 0 velocity)))
  (animate  [this [time state]]
      (let [{:keys [from velocity deceleration interpolation duration]} config
            v (-  velocity (/ (* deceleration time) 1000))
            s (- (/ (* velocity time) 1000) (/ (* deceleration time time) 2 1000 1000))]
         {:value     (+ from (interpolation s))
          :velocity v
          :time     time}))
  (start-with-notify! [this cb] (utils/perform-start! this stops-ch ticks-ch (utils/config->state config) cb))
  (update!  [this {:keys [value]}]
    (let [update! (:update! config)]
      (if (some? update!) (update! value) (reset! *value value))))
  (start!   [this] (utils/perform-start! this stops-ch ticks-ch (utils/config->state config)))
  (stop!    [_] (async/put! stops-ch {:finished false}))
  ; (stop!    [this] (async/close! stops-ch))
  (ticks-ch [_] ticks-ch)
  (stop-ch  [_] stops-ch))

(defn decay [*value config']
  (let [stops (async/chan)
        ticks (async/chan)]
    (Decay. *value (merge config config') stops ticks)))
