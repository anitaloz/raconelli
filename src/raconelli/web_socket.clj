
(ns raconelli.web_socket
  (:require [org.httpkit.server :as server]
            [clojure.data.json :as json]
            [raconelli.game-state :as game-state]
            [raconelli.physics :as physics]))

(defn handle-player-input [player-id input-data]
  (game-state/update-player! player-id #(physics/handle-player-input % input-data)))

(defn create-websocket-handler []
  (fn [request]
    (server/with-channel request channel
                         (println "Client connected")

                         (let [player-id (str (java.util.UUID/randomUUID))]
                           ;; Add new player to game
                           (game-state/add-player! player-id channel)

                           (server/on-close channel
                                            (fn [status]
                                              (println "Client disconnected:" player-id)
                                              (game-state/remove-player! player-id)))

                           (server/on-receive channel
                                              (fn [data]
                                                (try
                                                  (let [message (json/read-str data :key-fn keyword)]
                                                    (case (:type message)
                                                      "player-input" (handle-player-input player-id (:input message))
                                                      "ping" (server/send! channel (json/write-str {:type "pong"}))
                                                      (println "Unknown message type:" (:type message))))
                                                  (catch Exception e
                                                    (println "Error processing message from" player-id ":" e)))))))))
