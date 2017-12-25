(ns clojure-animated.utils
  (:require [cljs.core.async :as async :refer [>! <!]]
            [clojure-animated.protocols :as protocols :refer [start! is-done? animate update!]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(def date-now js/Date.now)

(def schedule
  (or (and (exists? js/window)
           (or js/window.requestAnimationFrame
                js/window.webkitRequestAnimationFrame
                js/window.mozRequestAnimationFrame
                js/window.msRequestAnimationFrame))
    #(js/setTimeout % 16)))


(def stop-schedule
  (or (and (exists? js/window)
           (or js/window.cancelAnimationFrame
                js/window.webkitCancelAnimationFrame
                js/window.mozCancelAnimationFrame
                js/window.msCancelAnimationFrame))
    js/clearTimeout))



(defn schedule! []
  (let [ch (async/chan)]
    (schedule #(async/close! ch))
    ch))

(defn next-tick [start-date]
  (let [now (date-now)]
    (- now start-date)))

(defn raf->chan [ch animated initial-state]
  (let [start-date (date-now)]
    (go-loop [state initial-state]
      (>! ch state)
      (<! (schedule!))
      (recur (animate animated [(next-tick start-date) state])))
    ch))

(defn update-value! [animated ticks-chan]
  (go-loop []
    (let [state (<! ticks-chan)]
      (update! animated state)
      (recur))))



(defn perform-start! [animated stops ticks initial-state & [cb]]
  (let [start-date (date-now)
        update-ch (update-value! animated ticks)]
      (go-loop [state initial-state]
        (let [[_ c] (async/alts! [stops (schedule!)])]
          (cond
            (= c stops) (do (async/close! update-ch) (async/put! stops {:finished false}) (when (some? cb) (cb {:finished false})))
            (is-done? animated state) (do (async/put! stops {:finished true}) (when (some? cb) (cb {:finished true})))
            :else
              (do
                (>! ticks state)
                (recur (animate animated [(next-tick start-date) state]))))))
    animated))


(defn config->state [{:keys [velocity from start]
                      :as config
                      :or   {velocity 0
                             from     0
                             start    0}}]
  (do
    (if (some? (:ids config))
      config
      {:value    from
       :time     start
       :velocity velocity
       :active   false
       :raf-id   nil})))
