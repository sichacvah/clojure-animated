(ns clojure-animated.spring
  (:require [clojure-animated.protocols :as protocols :refer [IAnimated start! is-done? animate update!]]
            [cljs.core.async :as async :refer [<! >!]]
            [clojure-animated.utils :as utils])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(enable-console-print!)

(def config
  {:stiffness 100
   :damping   10
   :mass      10
   :from      0
   :to        360
   :treshold  0.01
   :velocity  0
   :type      :spring})

(defn eval-spring [dt x' v' a' {:keys [stiffness damping mass to from]}]
  (let [x (+ x' (* v' dt))
        v (+ v' (* a' dt))
        f (- (* stiffness (- to x)) (* damping v))
        a (/ f mass)]
    [v a]))

(defn rk4* [x v time config]
  (let [dt (min 1 (/ time 10))
        dt2 (* dt 0.5)
        [av aa] (eval-spring 0.0  x v 0.0 config)
        [bv ba] (eval-spring dt2 x av aa config)
        [cv ca] (eval-spring dt2 x bv ba config)
        [dv da] (eval-spring dt x cv ca config)
        dx (/ (+ av (* 2.0 (+ bv cv)) dv) 6.0)
        dv (/ (+ aa (* 2.0 (+ ba ca)) da) 6.0)]
      [(+ x (* dx dt)) (+ v (* dv dt))]))

(def rk4 (memoize rk4*))

(deftype Spring [*value config stops-ch ticks-ch]
  IAnimated
  (config [_] config)

  (is-done? [this state]
    (let [{:keys [to treshold]} config
          {:keys [value velocity]} state]
      (and (<= (js/Math.abs (- to value)) treshold)
           (<= (js/Math.abs velocity) treshold))))
  (animate  [this [time state]]
    (let [{:keys [to]} config
          prev-x (:value state)
          prev-v (:velocity state)
          prev-time (:time state)
          dt  (min 1 (/ (- time prev-time) 10.0))
          [x v] (rk4 prev-x prev-v dt config)]
        (if (is-done? this state)
          {:value to :time time :velocity 0}
          {:value x  :time time :velocity v})))
  (start-with-notify! [this cb] (utils/perform-start! this stops-ch ticks-ch (utils/config->state config) cb))
  (update!  [this {:keys [value]}]
    (let [update! (:update! config)]
      (if (some? update!) (update! value) (reset! *value value))))
  (start!   [this] (let [state (utils/config->state config)]
                      (utils/perform-start! this stops-ch ticks-ch state)))
  (stop!    [_] (async/put! stops-ch {:finished false}))
  (ticks-ch [_] ticks-ch)
  (stop-ch  [_] stops-ch))

(defn spring [*value config']
  (let [stops (async/chan)
        ticks (async/chan)]
      (Spring. *value (merge config config') stops ticks)))

