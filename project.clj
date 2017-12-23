(defproject clojure-animated "0.1.0-SNAPSHOT"
  :description    "ClojureScript animation library"
  :url            "https://github.com/sichacvah/clojure-animated"
  :license        {:name "Eclipse Public License"
                   :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies   [[org.clojure/clojure "1.9.0-alpha10"]
                   [org.clojure/clojurescript "1.9.89"]
                   [org.clojure/core.async "0.3.443"]
                   [rum "0.10.8"]]

  :plugins        [[lein-cljsbuild "1.1.7"]]

  :profiles       {:dev {:source-paths ["examples"]}}

  :aliases        {"package" ["do" ["clean"] ["test"] ["clean"] ["cljsbuild" "once" "advanced"]]}

  :cljsbuild
  {:builds
    [{:id "advanced"
      :source-paths ["src" "examples" "test"]
      :compiler
      {:main            clojure-animated.examples
       :output-to       "target/main.js"
       :optimizations   :advanced
       :source-map      "target/main.js.map"
       :pretty-print    false
       :compiler-stats true
       :parallel-build  true}}

     {:id "none"
      :source-paths ["src" "examples" "test"]
      :compiler
      {:main            clojure-animated.examples
       :output-to       "target/main.js"
       :output-dir      "target/none"
       :asset-path      "target/none"
       :optimizations   :none
       :source-map      true
       :compiler-stats  true
       :parallel-build  true}}

     {:id "test"
      :source-paths ["src" "test"]
      :compiler
      {:main clojure-animated.test.core
       :output-to       "target/test.js"
       :output-dir      "target/test"
       :asset-path      "target/test"
       :optimizations   :advanced
       :pretty-print    true
       :pseudo-names    true
       :parallel-build  true}}]})

