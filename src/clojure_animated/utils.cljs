(ns clojure-animated.utils
  (:require [cljs.core.async :as async :refer [>! <!]]
            [clojure-animated.protocols :as protocols :refer [start! is-done? animate update!]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(def date-now js/Date.now)


(def ^:private schedule
  (or (and (exists? js/window)
           (or js/window.requestAnimationFrame
                js/window.webkitRequestAnimationFrame
                js/window.mozRequestAnimationFrame
                js/window.msRequestAnimationFrame))
    #(js/setTimeout % 16)))

(defn schedule! []
  (let [ch (async/chan)]
    (schedule #(async/close! ch))
    ch))

(defn next-tick [last-date]
  (let [now (date-now)]
    (- now last-date)))

(defn ticks->chan []
  (let [last-date (date-now)
        ch (async/chan)]
    (go-loop [clock 0]
      (>! ch clock)
      (<! (schedule!))
      (recur (next-tick last-date)))
    ch))


(defn update-value! [animated ticks-chan]
  (go-loop []
    (let [[val state] (<! ticks-chan)]
      (update! animated val)
      (recur))))

(defn perform-start! [animated stops ticks & [*state]]
  (let [time-ch (ticks->chan)
        last-date (date-now)]
      (update-value! animated ticks)
      (go-loop []
        (let [[time c] (async/alts! [stops time-ch])
              state    (when (some? *state) @*state)
              next     (animate animated [time state])]
          (cond
            (= c stops) (do (>! stops {:finished false}) (async/close! stops))
            (is-done? animated [time state]) (do (>! stops {:finished true}) (async/close! stops))
            :else (do
                    (>! ticks [next state])
                    (recur)))))
    animated))
