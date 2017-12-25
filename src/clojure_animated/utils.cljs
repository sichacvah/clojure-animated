(ns clojure-animated.utils)


(defn config->state [{:keys [velocity from start]
                      :as config
                      :or   {velocity 0
                             from     0
                             start    0}}]
    (if (some? (:ids config))
      config
      {:value    from
       :time     start
       :velocity velocity}))
