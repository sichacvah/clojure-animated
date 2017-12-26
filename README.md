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

### Animation composition

```clojure
(animated/order [(animated/parallel [(animated/spring *angle {:from 0 :to 3600 :stiffness 20 :damping 4 :mass 3})
                                     (animated/timing *cx {:from 450 :to 1500 :duration 3000 :ease identity})])
                 (animated/parallel [(animated/spring *angle {:from 0 :to 3600 :stiffness 20 :damping 4 :mass 3})
                                     (animated/timing *cy {:from 550 :to 1000 :ease identity :duration 3000})])
                 (animated/parallel [(animated/spring *cx {:from 1500 :to 450})
                                     (animated/spring *angle {:from 0 :to 20000})
                                     (animated/spring *cy {:from 1500 :to 550})])])
```

## License

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
