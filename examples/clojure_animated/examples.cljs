(ns clojure-animated.examples
  (:require
    [rum.core :as rum]
    [cljs.core.async :as async]
    [clojure-animated.animated :as animated])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(enable-console-print!)

(defn get-annulus [r0' r1' r2' r3']
  [r1' r0' r2' (+ r2' 20)])

(defn get-radius [radius annulus]
  (let [r2 (js/Math.abs radius)
        r0 (- r2 8)
        r1 (+ r2 8)
        r3 20]
      (if annulus (get-annulus r0 r1 r2 r3) [r0 r1 r2 r3])))

(def pi (.-PI js/Math))
(def cos js/Math.cos)
(def sin js/Math.sin)
(defn get-teeth [i r0 r1 r2 r3 da a0']
  (let [a0 (+ a0' (* 2 da i) da)]
    [ "A" r0 "," r0 " 0 0,1 " (* r0 (cos (- a0 da))) "," (* r0 (sin (- a0 da)))
      "L" (* r2 (cos (- a0 da))) "," (* r2 (sin (- a0 da)))
      "L" (* r1 (cos (- a0 (* (/ 2 3) da)))) "," (* r1 (sin (- a0 (* (/ 2 3) da))))
      "A" r1 "," r1 " 0 0,1 " (* r1 (cos (- a0 (* (/ 1 3) da)))) "," (* r1 (sin (- a0 (* (/ 1 3) da))))
      "L" (* r2 (cos a0)) "," (* r2 (sin a0))
      "L" (* r0 (cos a0)) "," (* r0 (sin a0))]))

(defn gear [teeth radius annulus & args]
  (let [[r0 r1 r2 r3'] (get-radius radius annulus)
        in-r (if (some? (first args)) (first args) 20)
        r3 (if annulus r3' in-r)
        da (/ pi teeth)
        a0 (- (if annulus (/ pi teeth) 0) (/ pi 2))
        path [" M" (* r0 (cos a0)) "," (* r0 (sin a0))]]
    (->>  (reduce (fn [acc i]  (concat acc (get-teeth i r0 r1 r2 r3 da a0))) path (range teeth))
          (vec)
          (#(concat % ["Z M0," (- 0 r3) "A" r3 "," r3 " 0 0,0 0," r3 "A" r3 "," (- r3) " 0 0,0 0, " (- 0 r3) " Z"]))
          (apply str))))

(defn translate [x y]
  (str "translate(" x "," y ")"))

(defn rotate [angle]
  (str "rotate(" angle ")"))

(defn init-animation []
  {:start     0
    :delay     0
    :duration  3600
    :ease      identity
    :interpolation identity
    :from      0
    :to        3600
    :type      :timing})


(defn animate-angle [state]
  (let [*angle (::angle state)
        *cy    (::cy state)
        *cx    (::cx state)
        animation (animated/start!
                      (animated/parallel [(animated/timing *cx {:from 450 :to 1500 :duration 1500})
                                          (animated/timing *cy {:from 450 :to 1000 :duration 1500})
                                          (animated/spring *angle {:from 0 :to 3600})])
                      (fn [x] x))]
        ; animation (animated/start! (animated/spring *angle {:from 0 :to 3600}) (fn [x] x))]
        ; animation (animated/start!
        ;             (animated/order
        ;               [(animated/parallel      [(animated/timing *cx    {:from 450 :to 1500 :duration 1000})
        ;                                         (animated/timing *cy    {:from 450 :to 1000 :duration 1000})])
        ;                (animated/parallel      [(animated/spring *angle {:from 0 :to 3600})
        ;                                         (animated/timing *cy     {:from 1000 :to 450 :duration 1000})])
        ;                (animated/decay *cx {:from 1500 :velocity 100   :interpolation (fn [x] (- x))})]))]
      (assoc state :animation animation)))

(rum/defc annulus < rum/static
  [radius]
  (let [g (gear 80 radius true)]
     [:path {:d g}]))

(rum/defc sun < rum/static
  [radius]
  (let [g (gear 16 radius false)]
    [:path {:d g}]))

(def x (sin (/ (* 2 pi) 3)))
(def y (cos (/ (* 2 pi) 3)))

(rum/defc plannet < rum/static
  [radius]
  (let [g (gear 32 radius false)]
    [:path {:d g}]))

(rum/defc rotor < rum/static [angle radius [x y] class component]
  [:g {:class class :transform (str (translate x y) (rotate (- (/ angle radius))))} component])

(rum/defcs planetar < rum/static (rum/local 0 ::angle) (rum/local 450 ::cy) (rum/local 450 ::cx)
  {:did-mount animate-angle}
  [state]
  (let [angle @(::angle state)
        animation (:animation state)
        radius 80
        annulus-radius (* 5 radius)
        planet-radius  (* 2 radius)
        cx @(::cx state)
        cy @(::cy state)]
    [:g {:on-click (fn [event] (do (.preventDefault event) (animated/stop! animation)))}
      [:g {:transform "scale(0.5)"}
        (rotor angle (- annulus-radius) [cx cy] "annulus" (annulus annulus-radius))
        (rotor angle (- planet-radius) [cx (- cy (* radius 3))] "planet" (plannet planet-radius))
        (rotor angle (- planet-radius) [(+ cx (* x radius 3)) (- cy (* y radius 3))] "planet" (plannet planet-radius))
        (rotor angle (- planet-radius) [(- cx (* x radius 3)) (- cy (* y radius 3))] "planet" (plannet planet-radius))
        
        ; (plannet [(+ cx (* x 80 3)) (- cy (* y 80 3))] angle)
        ; (plannet [(+ cx (* x 80 3)) (- cy (* y 80 3))] angle)
        (rotor angle radius [cx cy] "sun" (sun radius))]
      [:text {:x 110 :y 50 :fill "#000" :stroke "#000" :font-size "18px" :width "500px" :height "150px"} "CLICK FOR STOP ANIMATION"]]))

(rum/defc canvas < rum/static []
  [:svg {:width   "1000"
         :height "1000"
         :xmlns   "http://www.w3.org/2000/svg"
         :id     "svg-canvas"}
      [:g
        (planetar)]])

(rum/mount (canvas) (. js/document getElementById "app"))


