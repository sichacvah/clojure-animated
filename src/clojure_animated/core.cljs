(ns clojure-animated.core
  (:require [cljs.core.async :as async :refer [<! >!]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))  

(enable-console-print!)

(defn ->speed [duration from to]
  (/ (js/Math.abs (- to from)) duration))

(defn ->duration [speed from to]
  (/ (js/Math.abs (- to from)) speed))


(def default-duration 750)
(def pi (.-PI js/Math))
(def cos js/Math.cos)

(defn default-ease [x]
  (/ (- 1 (js/Math.cos (* pi x))) 2))

(defn default-interpolation [x]
  x)

(defn init [time]
  {:start     time
   :delay     0
   :duration  default-duration
   :ease      default-ease
   :interpolation identity
   :from      0
   :to        1})


(defn static [x]
  {:start    0
   :delay    0
   :duration default-duration
   :ease     default-ease
   :interpolation identity
   :from     x
   :to       x})


(defn get-duration [animation]
  (let [duration (:duration animation)
        speed    (:speed    animation)]
    (if (some? duration) duration (->duration speed (:from animation) (:to animation)))))

(defn clamp [from to num]
  (cond
    (< num from) from
    (>= num to)  to
    :else        num))


(defn ->correction [ramp fr time start]
  (if (nil? ramp) 0
   (let [eased (default-ease fr)
         from  (* ramp (- time start))]
       (- from (* from eased)))))


(defn is-static [animation]
  (= (:from animation) (:to animation)))


(defn is-done [time animation]
  (let [duration (get-duration animation)
        {:keys [start delay from to]} animation]
       (or (is-static animation) (>= time (+ start delay duration)))))

(defn animate [time animation]
  (let [duration (get-duration animation)
        {:keys [start delay speed ramp from to ease interpolation]} animation
        fr (clamp 0 1 (/ (- time start delay) duration))
        eased (interpolation (ease fr))
        correction (if (some? ramp) (->correction ramp fr time start) 0)]
       (if (is-done time animation)
          to
          (+ from correction (* (- to from) eased)))))


(defn time-remaining [time {:keys [start delay from to] :as animation}]
  (let [duration (get-duration animation)]
       (max 0 (- time (+ start delay duration)))))

(defn undo [time animation]
  {:from (:to animation)
   :to   (:from animation)
   :interpolation (:interpolation animation)
   :start time 
   :delay (- (time-remaining time animation))
   :ease  (fn [t] (- 1 ((:ease animation) (- 1 t))))})


(defn time-elapsed [time {:keys [start delay]}]
  (max 0 (- time start delay)))

(def pow js/Math.pow)
(def sin js/Math.sin)
(defn elastic-ease [n]
  (+ 1 (* (pow 2 (* (- 10) n)) (sin (/ (* (- n 0.075) 2 pi) 0.3)))))


(defn velocity [time animation]
  (let [back-diff (animate (- time 10) animation)
        forw-diff (animate (+ time 10) animation)]
      (/ (- forw-diff back-diff) 20)))

(defn duration [d animation]
  (assoc (dissoc animation :speed) :duration d))

(defn speed [s animation]
  (assoc (dissoc animation :duration) :speed s))

(defn delay' [d animation]
  (assoc animation :delay d))

(defn ease [e animation]
  (assoc animation :ease e))

(defn from [f animation]
  (assoc animation :from f))

(defn to [t animation]
  (assoc animation :to t))

(defn interpolation [i animation]
  (assoc animation :interpolation i))

(defn equals [a1 a2]
  (and (= (+ (:start a1) (:delay a1)) (+ (:start a2) (:delay a2)))
       (= (:from a1) (:from a2))
       (= (:to a1) (:to a2))
       (= (:ramp a1) (:ramp a2))
       (= (:speed a1) (:speed a2))
       (= (:duration a1) (:duration a2))
       (every? (fn [x] (= ((:ease a1) x) ((:ease a2) x))) [0.1 0.3 0.7 0.9])))


(defn is-scheduled [time {:keys [start delay] :as animation}]
  (and (<= time (+ start delay)) (not (is-static animation))))

(defn is-running [time {:keys [start delay from to] :as animation}]
  (let [duration (get-duration animation)]
    (and (> time (+ start delay)) (< time (+ start delay duration)) (not (is-static animation)))))


(defn get-speed [animation]
  (let [speed (:speed animation)]
    (if (some? speed) speed (->speed (:duration animation) (:from animation) (:to animation)))))


(defn retarget-running [time next-to animation]
  (let [vel (velocity time animation)
        pos (animate time animation)
        next-speed (get-speed animation)]
      ({:start time :delay 0 :speed next-speed :ramp vel :ease (:ease animation) :from pos :to next-to})))

(defn retarget [time next-to animation]
  (cond (= next-to (:to animation)) animation
        (is-static animation) (dissoc (assoc animation :start time :to next-to) :ramp)
        (is-scheduled time animation) (dissoc (assoc animation :start time :delay 0 :from (:to animation) :to next-to) :ramp)
        (is-done time animation) (dissoc (assoc animation :start time :delay 0 :from (:to animation) :to next-to) :ramp)
        :else (retarget-running time next-to animation)))


;; Try emulate RN Animated API

(def ^:private schedule
  (or (and (exists? js/window)
           (or js/window.requestAnimationFrame
               js/window.webkitRequestAnimationFrame
               js/window.mozRequestAnimationFrame
               js/window.msRequestAnimationFrame))
      #(js/setTimeout % 16)))

(def ^:private stop-schedule
  (or (and (exists? js/window)
           (or js/window.cancelAnimationFrame
               js/window.webkitCancelAnimationFrame
               js/window.mozCancelAnimationFrame
               js/window.msCancelAnimationFrame))
      js/clearTimeout))

(def date-now js/Date.now)

(defn init-timing []
  {:start 0
   :from  0
   :to    1
   :ease  default-ease
   :intperpolation identity
   :duration default-duration
   :delay 0
   :type  :timing})


(defn schedule! []
  (let [ch (async/chan)]
    (schedule #(async/close! ch))
    ch))


(defmulti start- :type)

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

(defmethod start- :timing [config *value]
  (let [stops (async/chan)
        ticks (ticks->chan)
        last-date (date-now)]
      (go-loop []
        (let [[v c] (async/alts! [stops ticks])]
          (reset! *value (animate v config))
          (cond
            (= c stops) (do (println "is stopped") (>! stops {:finished false}) (async/close! stops))
            (is-done v config) (do (println "is done") (>! stops {:finished true}) (async/close! stops))
            :else
              (do
                (recur)))))
    stops))

(defmethod start- :default [& _] (println (str "no method allowed")))
