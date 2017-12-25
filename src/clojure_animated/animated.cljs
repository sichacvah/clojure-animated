(ns clojure-animated.animated
  (:require [cljs.core.async :as async :refer [>! <!]]
            [clojure-animated.utils :as utils])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(def now js/Date.now)

(def schedule!
  (or (and (exists? js/window)
           (or js/window.requestAnimationFrame
                js/window.webkitRequestAnimationFrame
                js/window.mozRequestAnimationFrame
                js/window.msRequestAnimationFrame))
    #(js/setTimeout % 16)))

(def stop-schedule!
  (or (and (exists? js/window)
           (or js/window.cancelAnimationFrame
                js/window.webkitCancelAnimationFrame
                js/window.mozCancelAnimationFrame
                js/window.msCancelAnimationFrame))
    js/clearTimeout))

(defn next-tick! [start-date]
    (- (now) start-date))

(def pi (.-PI js/Math))
(def cos js/Math.cos)

(defn default-ease [x]
  (/ (- 1 (js/Math.cos (* pi x))) 2))

(defn clamp [from to num]
  (cond
    (< num from) from
    (>= num to)  to
    :else        num))

(defprotocol CompositeAnimation
  (start!   [this cb] "start animation")
  (stop!    [this]    "stop animation"))

(defprotocol Animation
  (is-done? [this state] "return boolean")
  (update!  [this state] "update value")
  (animate  [this state] "get next state"))

(defn perform-stop! [*state]
  (let [{:keys [raf-id cb]} @*state]
      (utils/stop-schedule raf-id)
      (swap! *state assoc :raf-id nil :active false)
      (cb {:finished false})))

(defn perform-step! [animated *state start-date cb]
  (do
   (swap!
    *state
    assoc
    :raf-id
    (schedule! (fn []
                  (let [time (- (now) start-date)
                        state (animate animated [time @*state])]
                    (update! animated state)
                    (if (is-done? animated state)
                        (do (swap! *state #(assoc (merge % state) :active false :raf-id nil))
                            (cb {:finished true}))
                        (do (swap! *state merge state) (perform-step! animated *state start-date cb)))))))))

(defn perform-start! [animated *state cb]
  (let [start-date (now)]
    (swap! *state assoc :cb cb :active true)
    (perform-step! animated *state start-date cb)))

(deftype Timing [config *value *state]
  Animation
  (update! [this state]
    (reset! *value (:value state)))
  (is-done? [this state]
    (let [{:keys [start delay from to duration]} config
          time (:time state)]
      (>= time (+ start delay duration))))
  (animate  [this [time state]]
    (let [{:keys [start delay speed ramp from to ease interpolation duration]} config
          fr (clamp 0 1 (/ (- time start delay) duration))
          eased (interpolation (ease fr))]
     (if (is-done? this [time])
        (assoc state :value to :time time)
        (assoc state :value (+ from (* (- to from) eased)) :time time))))
  CompositeAnimation
  (start! [this cb] (perform-start! this *state cb))
  (stop!  [_]    (perform-stop! *state)))



(def timing-config
  {:start          0
   :from           0
   :to             1
   :ease           default-ease
   :interpolation identity
   :duration       750
   :delay          0
   :type           :timing})

(defn timing [*value config]
  (let [cfg (merge timing-config config)]
    (Timing. cfg *value (atom (utils/config->state cfg)))))


(def spring-config
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

(deftype Spring [config *value *state]
  CompositeAnimation
  (start! [this cb] (do (perform-start! this *state cb) this))
  (stop!  [_]    (perform-stop! *state))
  Animation
  (update! [this state]
    (reset! *value (:value state)))
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
          (assoc state :value to :time time :velocity 0)
          (assoc state :value x  :time time :velocity v)))))

(defn spring [*value config]
  (let [cfg (merge spring-config config)]
    (Spring. cfg *value (atom (utils/config->state cfg)))))

(defn composite-callback [end-result animation animations-count id cb *state]
  (do (swap! *state assoc-in [:has-ended id] true)
    (when (= (count (:has-ended @*state)) animations-count)
      (cb end-result))))

(deftype Parallel [animations *state]
  CompositeAnimation
  (start! [this cb]
    (let [animations' (map-indexed vector animations)
          animations-count (count animations')]
      (swap! *state assoc :cb cb)
      (doseq [[idx animation] animations']
        (start! animation #(composite-callback % animation animations-count idx cb *state)))
      this))
  (stop!  [this]
    (let [animations' (map-indexed vector animations)
          {:keys [has-ended]} @*state]
      (doseq [[idx animation] animations']
        (when (not (contains? idx has-ended))
              (stop! animation))))))


(defn parallel [animations]
  (Parallel. animations (atom {:has-ended {} :cb nil})))
