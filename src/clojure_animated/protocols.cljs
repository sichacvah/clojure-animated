(ns clojure-animated.protocols)

(defprotocol IAnimated
  "Protocol for all animated types"
  (is-done? [this state] "return boolean value")
  (animate  [this state] "return value by time")
  (update!  [this time] "update value")
  (stop-ch  [this] "return stop chanel")
  (start!   [this] "start's animation")
  (config   [this] "return animation config")
  (stop!    [this] "stops animation")
  (ticks-ch [this] "return ticks ch"))
