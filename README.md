# clojure-animated

A Clojurescript lib for animate things

## Usage

```
(require '(cljs.core.async :as async))
(require '(clojure-animated.core :as animated))

; create some value to animate
(def *height (atom 0))

(add-watch *height :watcher
  (fn [key atom old new]
    (println "height- " new)))

; init animation-conifg
(def config (animated/init))

; start animation

(def stop-chan (animated/start- config *height))

; if you want to stop animation. you cat put to stop-chan

(comment
  (async/put! stop-chan nil))


```

## License

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
