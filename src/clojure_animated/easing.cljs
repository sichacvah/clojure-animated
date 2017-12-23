(ns clojure-animated.easing)

(def pi (.-PI js/Math))
(def cos js/Math.cos)
(def pow js/Math.pow)
(def sin js/Math.sin)

(defn default-ease [x]
  (/ (- 1 (js/Math.cos (* pi x))) 2))

(defn elastic-ease [n]
  (+ 1 (* (pow 2 (* (- 10) n)) (sin (/ (* (- n 0.075) 2 pi) 0.3)))))