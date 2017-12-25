# clojure-animated

A Clojurescript lib for animate things

## Usage

```clojure
(require '(clojure-animated.core :as animated))

; create some value to animate
(def *height (atom 0))

(add-watch *height :watcher
  (fn [key atom old new]
    (println "height- " new)))

; init animation
(def timing (animated/timing *height {:from 0 :to 100 :duration 1000}))

; start animation

(def animation (animated/start! timing)

; if you want to stop animation. you cat put to stop-chan

(comment
  (animated/stop! animation))

```

You can also compose animations

```clojure
(animated/order    [(animated/timing *cx {:from 450 :to 1500 :duration 1500})
                                      (animated/parallel [(animated/spring *angle {:from 0 :to 3600})
                                                          (animated/timing *cy {:from 450 :to 1000 :duration 1500})])])
```

## License

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
