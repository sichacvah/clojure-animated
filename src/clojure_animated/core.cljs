(ns clojure-animated.core
  (:require [clojure-animated.utils :as utils]))

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
  (set-raf! [this r] "set request animation frame id")
  (get-raf  [this] "get request animation frame id")
  (set-cb!  [this cb] "set on-finish callback")
  (get-cb   [this] "get on-finish callback")
  (is-done? [this state] "return boolean")
  (update!  [this state] "update value")
  (animate  [this state] "get next state")
  (active?  [this] "boolean"))

(defn perform-stop! [animated raf cb]
  (let []
      (stop-schedule! raf)
      (set-raf! animated nil)
      (cb {:finished false})))

(defn perform-step! [animated state start-date cb]
  (set-raf!
    animated
    (schedule! (fn []
                  (let [time (- (now) start-date)
                        next-state (animate animated [time state])]
                    (update! animated next-state)
                    (if (is-done? animated next-state)
                        (do (set-raf! animated nil) (cb {:finished true}))
                        (perform-step! animated next-state start-date cb)))))))


(defn perform-start! [animated state cb]
  (let [start-date (now)]
    (perform-step! animated state start-date cb)))


(deftype Timing [config *value state ^:volatile-mutable raf ^:volatile-mutable cb]
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
  (active? [_] (some? raf))
  (set-raf! [this r] (set! raf r))
  (get-raf  [this] raf)
  (set-cb!  [this cb'] (set! cb cb'))
  (get-cb   [this] cb)
  CompositeAnimation
  (stop! [this] (when (active? this) (perform-stop! this raf cb)))
  (start! [this cb]
    (do
      (set-cb! this cb)
      (when (not (active? this)) (perform-start! this state cb)))))



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
    (Timing. cfg *value (utils/config->state cfg) nil nil)))
; config *value state *raf

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

(deftype Spring [config *value state ^:volatile-mutable raf ^:volatile-mutable cb]
  CompositeAnimation
  (stop! [this] (when (active? this) (perform-stop! this raf cb)))
  (start! [this cb]
    (do
      (set-cb! this cb)
      (when (not (active? this)) (perform-start! this state cb))))
  Animation
  (active? [_] (some? raf))
  (set-raf! [this r] (set! raf r))
  (get-raf  [this] raf)
  (set-cb!  [this cb'] (set! cb cb'))
  (get-cb   [this] cb)
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
    (Spring. cfg *value (utils/config->state cfg) nil nil)))

(defn composite-callback [end-result animation animations-count id cb *state]
  (do (swap! *state assoc-in [:has-ended id] true)
    (when (= (count (:has-ended @*state)) animations-count)
      (do (cb end-result)
          (swap! *state assoc :has-ended {})))))

(deftype Parallel [animations *state]
  CompositeAnimation
  (start! [this cb]
    (when (= 0 (count (:has-ended @*state)))
      (let [animations' (map-indexed vector animations)
            animations-count (count animations')]
        (swap! *state assoc :cb cb)
        (doseq [[idx animation] animations']
          (start! animation #(composite-callback % animation animations-count idx cb *state)))
        this)))
  (stop! [this]
    (let [animations' (map-indexed vector animations)
          {:keys [has-ended]} @*state]
      (doseq [[idx animation] animations']
        (when (not (contains? idx has-ended))
              (stop! animation))))))


(defn parallel [animations]
  (Parallel. animations (atom {:has-ended {} :cb nil})))

(deftype Order [animations ^:volatile-mutable animations-to-run ^:volatile-mutable cb]
  CompositeAnimation
  (start! [this cb']
    (let [animations-count (count animations-to-run)]
      (if (= 0 animations-count)
        (do (set! animations-to-run animations) (cb {:finished true}))
        (let [animation (first animations-to-run)]
          (when (nil? cb) (set! cb cb'))
          (start! animation (fn [end-result] 
                              (do (set! animations-to-run (rest animations-to-run))
                                  (start! this cb))))
          this))))
  (stop! [this]
    (let []
      (doseq [animation animations-to-run]
        (when (active? animation) (stop! animation)))
      (when (some? cb) (cb {:finished false})))))


(defn order [animations]
  (Order. animations animations nil))
