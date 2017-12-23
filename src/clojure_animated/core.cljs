(ns clojure-animated.core
  (:require [cljs.core.async :as async :refer [<! >!]]
            [clojure-animated.spring :as spring]
            [clojure-animated.timing :as timing]
            [clojure-animated.decay  :as decay]
            [clojure-animated.protocols :as protocols])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))  

(enable-console-print!)

(def decay  decay/decay)
(def timing timing/timing)
(def spring spring/spring)

(def start!   protocols/start!)
(def is-done? protocols/is-done?)
(def animate  protocols/animate)
(def update!  protocols/update!)
(def stop!    protocols/stop!)