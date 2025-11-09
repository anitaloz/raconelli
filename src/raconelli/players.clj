(ns raconelli.players)

(defn create-player [id]
  {:id id
   :x 100
   :y 100
   :velocity-x 0
   :velocity-y 0
   :angle 0
   :color (rand-nth ["#FF0000" "#00FF00" "#0000FF" "#FFFF00" "#FF00FF" "#00FFFF"])
   :last-update (System/currentTimeMillis)
   :name (str "Player-" (subs id 0 6))})

(defn get-player-color [player-id players]
  (get-in players [player-id :color] "#FFFFFF"))

(defn get-player-position [player-id players]
  (let [player (get players player-id)]
    (when player
      {:x (:x player) :y (:y player) :angle (:angle player)})))

(defn get-all-players-positions [players]
  (into {} (map (fn [[id player]] [id (select-keys player [:x :y :angle :color])]) players)))