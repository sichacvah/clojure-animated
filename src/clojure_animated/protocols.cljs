(ns clojure-animated.protocols)

(defprotocol IAnimated
  "Protocol for all animated types"
  (is-done? [this state] "return boolean value")
  (animate  [this state] "return value by time")
  (update!  [this time] "update value")
  (start!   [this] "start's animation")
  (stop!    [this] "stops animation"))
