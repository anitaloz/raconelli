(ns raconelli.server
  (:require [org.httpkit.server :as server]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [clojure.data.json :as json]
            [raconelli.game-state :as game-state]
            [raconelli.web_socket :as websocket]
            [raconelli.physics :as physics])
  (:gen-class))

(defn update-game-loop []
  (game-state/update-game-state!
    (fn [state]
      (-> state
          (update :players
                  (fn [players]
                    (into {}
                          (map (fn [[id player]]
                                 [id (physics/apply-physics player)]))
                          players)))
          (update :game-time + 0.016)))))

(defn broadcast-game-state []
  (let [state (game-state/get-game-state)]
    (game-state/broadcast-to-all!
      (fn [channel]
        (server/send! channel
                      (json/write-str
                        {:type "game-state"
                         :state {:players (:players state)
                                 :game-time (:game-time state)}}))))))

(defn game-loop []
  (future
    (println "Starting game loop...")
    (loop []
      (Thread/sleep 16) ; ~60 FPS
      (try
        (update-game-loop)
        (broadcast-game-state)
        (catch Exception e
          (println "Error in game loop:" e)))
      (recur))))

;; HTTP routes
(defroutes app-routes
           (GET "/ws" [] (websocket/create-websocket-handler))
           (GET "/" []
                (slurp (clojure.java.io/resource "public/index.html")))
           (GET "/status" []
                {:status 200
                 :headers {"Content-Type" "application/json"}
                 :body (json/write-str
                         {:players (count (game-state/get-players))
                          :game-time (:game-time (game-state/get-game-state))})})
           (route/resources "/")
           (route/not-found "Not Found"))

(defn -main [& args]
  (let [port (or (System/getenv "PORT") 8080)]
    (println "Starting racing game server on port" port)
    (game-loop)
    (server/run-server #'app-routes {:port (Integer. port)})))