(ns clojure-animated.collection
  (:require [cljs.core.async :as async :refer [>! <!]]
            [clojure-animated.utils :as utils]
            [clojure-animated.protocols :as protocols :refer [IAnimated stop! stop-ch start! config is-done? animate update! ticks-ch]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))


(defn anim-by-id [{:keys [animations]} id]
  (get animations id))

(defn state-by-id [{:keys [states]} id]
  (get states id))

(defn normalize-animations [animations]
  (let [ids (map-indexed (fn [id] id) animations)]
    {:ids ids
     :animations (reduce (fn [acc id] (assoc acc id (get animations id))) {} ids)
     :states (reduce (fn [acc id]
                        (assoc acc id (->> id
                                           (get animations)
                                           (config)
                                           (utils/config->state))))
                  {} ids)}))
; utils/config->state (config (anim-by-id animations id))

(defn get-final-state [state prev-state]
  (if (nil? prev-state)
      state
      (assoc state :time (- (:time state) (:time prev-state)))))

(defn find-running [{:keys [states ids animations]}]
  (loop [ids' ids
         prev-id nil]
    (let [id (first ids')]
      (when (some? id)
        (if (is-done? (get animations id) (get-final-state (get states id) (get states prev-id)))
            (do (println id (get states id)) (recur (rest ids') id))
            id)))))

(deftype Order [normalized-animations stops-ch ticks]
  IAnimated
  (config [_] normalized-animations)
  (ticks-ch [_] ticks)
  (stop-ch [_] stops-ch)
  (stop! [this] (do (async/put! stops-ch {:finished false})
                    (doseq [[id anim] (:animations normalized-animations)]
                      (stop! anim))))
  (start! [this] (let [{:keys [animations]} normalized-animations]
                    (go-loop [ids (:ids normalized-animations)]
                      (if (nil? (first ids))
                        (async/put! stops-ch {:finished true})
                        (let [id (first ids)
                              anim (get animations id)
                              anim-ticks (ticks-ch anim)
                              stop       (stop-ch anim)]
                          (async/pipe anim-ticks ticks)
                          (async/pipe stops-ch stop)
                          (start! (get animations id))
                          (let [stop-result (<! stop)]
                             (if (and (:finished stop-result))
                                (recur (rest ids))
                                (>! stop stop-result))))))
                    this)))

(deftype Parallel [normalized-animations stops-ch ticks-ch]
  IAnimated
  (ticks-ch [_] ticks-ch)
  (stop-ch [_] stops-ch)
  (config [_] normalized-animations)
  (stop! [this] (let [{:keys [animations ids]} normalized-animations]
                    (async/put! stops-ch {:finished false})
                    (doseq [id ids]
                      (stop! (get animations id)))))
  (start! [this] (let [{:keys [animations ids]} normalized-animations]
                    (let []
                      (go-loop [ids ids]
                        (if (empty? ids)
                            (async/put! stops-ch {:finished true})
                            (let [stop (stop-ch (get animations (first ids)))
                                  stop-result (<! stop)]
                              ; (>! stop stop-result)
                              (recur (rest ids)))))
                      (doseq [id ids]
                        (start! (get animations id))))
                    this)))




; (deftype Order [animations stops-ch ticks-ch]
;   IAnimated
;   (config [_] animations)
;   (stop! [this] (async/put! stops-ch {:finished false}))
;   (is-done? [this state]
;     (nil? (find-running state)))
;   (animate [this [time state]]
;     (let [{:keys [ids states animations]} state
;           id (find-running state)
;           animation (get animations id)]
;       (assoc-in state [:states id] (animate animation [time (get states id)]))))
;   (update! [this {:keys [states animations] :as state}]
;     (let [id (find-running state)
;           animation (get animations id)]
;       (when (some? id)
;             (update! animation (get states id)))))
;   (stop-ch [this] stops-ch)
;   (start!  [this] (utils/perform-start! this stops-ch ticks-ch animations)))


; (deftype Parallel [animations stops-ch ticks-ch]
;   IAnimated
;   (config [_] animations)
;   (stop! [this] (async/put! stops-ch {:finished false}))
;   (is-done? [this {:keys [animations states ids]}]
;     (every? #(is-done? (get animations %) (get states %)) ids))
;   (animate [this [time state]]
;     (let [{:keys [states ids animations]} state
;           next-states (reduce (fn [acc id]
;                                   (assoc acc id (animate (get animations id) [time (get states id)])))
;                               {} ids)]
;       (assoc state :states next-states)))
;   (update! [this state]
;     (doseq [id (:ids state)]
;       (let [animated (anim-by-id state id)
;             animated-state (state-by-id state id)]
;           (update! animated animated-state))))
;   (stop-ch [this] stops-ch)
;   (start!  [this] (utils/perform-start! this stops-ch ticks-ch animations)))


(defn parallel [animations]
  (Parallel. (normalize-animations animations) (async/chan) (async/chan)))

(defn order [animations]
  (Order.
    (normalize-animations animations)
    (async/chan)
    (async/chan)))
