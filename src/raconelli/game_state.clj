(ns raconelli.game-state
  (:require [raconelli.players :as players]))

;; Global game state using refs for transactional updates
(def game-state
  (ref {:players {}
        :game-time 0}))

(def client-channels (ref {}))

(defn get-game-state []
  @game-state)

(defn update-game-state! [update-fn]
  (dosync
    (alter game-state update-fn)))

(defn add-player! [player-id channel]
  (dosync
    (alter client-channels assoc player-id channel)
    (alter game-state update :players assoc player-id (players/create-player player-id))))

(defn remove-player! [player-id]
  (dosync
    (alter client-channels dissoc player-id)
    (alter game-state update :players dissoc player-id)))

(defn update-player! [player-id update-fn]
  (dosync
    (alter game-state update-in [:players player-id] update-fn)))

(defn get-players []
  (:players @game-state))

(defn get-client-channels []
  @client-channels)

(defn broadcast-to-all! [message-fn]
  (doseq [[_ channel] (get-client-channels)]
    (when channel
      (try
        (message-fn channel)
        (catch Exception e
          (println "Error broadcasting to client:" e))))))