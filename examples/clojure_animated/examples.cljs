(ns clojure-animated.examples
  (:require
    [rum.core :as rum]
    [clojure-animated.core :as animated]))

(enable-console-print!)
(def count (atom 0))

(rum/defc counter < rum/reactive []
  [:div { :on-click (fn [_] (swap! count inc))}
    "Clicks: " (rum/react count)])

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

(defn animate-angle [state]
  (let [*angle (::angle state)
        timing (animated/timing! *angle (init-animation))
        start! (:start timing)]
      (start!)
      state))

(rum/defcs annulus < rum/static (rum/local 0 ::angle)
  {:did-mount animate-angle}
  [state coords]
  (let [radius 400
        g (gear 80 radius true)
        [x y] coords
        *angle (::angle state)]
    [:g {:class "annulus"} [:path {:d g :transform (str (translate x y) (rotate (- (/ @*angle radius))))}]]))

(rum/defcs sun < rum/static (rum/local 0 ::angle)
  {:did-mount animate-angle}
  [state coords]
  (let [radius 80
        g (gear 16 radius false)
        [x y] coords
        *angle (::angle state)]
    [:g {:class "sun"} [:path {:d g :transform (str (translate x y) (rotate ( / @*angle radius)))}]]))

(def x (sin (/ (* 2 pi) 3)))
(def y (cos (/ (* 2 pi) 3)))

(rum/defcs plannet < rum/static (rum/local 0 ::angle)
  {:did-mount animate-angle}
  [state coords]
  (let [[x y] coords
        radius 160
        g (gear 32 radius false)
        *angle (::angle state)]
    [:g {:class "planet"} [:path {:d g  :transform (str (translate x y) (rotate (- (/ @*angle radius))))}]]))



(defn init-animation []
  {:start     0
   :delay     0
   :duration  3600
   :ease      identity
   :interpolation identity
   :from      0
   :to        3600})


(rum/defc planetar < rum/static []
    [:g {:transform "scale(0.55)"}
      (annulus [450 450])
      (plannet [450 (- 450 (* 80 3))])
      (plannet [(+ 450 (* x 80 3)) (- 450 (* y 80 3))])
      (plannet [(- 450 (* x 80 3)) (- 450 (* y 80 3))])
      (sun [450 450])])
  
(rum/defc canvas < rum/static []
  [:svg {:width   "1000"
         :height "1000"
         :xmlns   "http://www.w3.org/2000/svg"
         :id     "svg-canvas"}
      (planetar)])

(rum/mount (canvas) (. js/document getElementById "app"))
