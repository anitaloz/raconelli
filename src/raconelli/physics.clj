(ns raconelli.physics)

;; Physics constants
(def max-speed 5)
(def acceleration 0.2)
(def friction 0.95)
(def rotation-speed 4)

(defn update-player-physics [player]
  (let [current-time (System/currentTimeMillis)
        delta-time (max 0.016 (/ (- current-time (:last-update player)) 1000.0)) ; cap delta-time
        new-vx (* (:velocity-x player) friction)
        new-vy (* (:velocity-y player) friction)
        new-x (+ (:x player) (* new-vx delta-time 60))
        new-y (+ (:y player) (* new-vy delta-time 60))]
    (assoc player
      :x new-x
      :y new-y
      :velocity-x new-vx
      :velocity-y new-vy
      :last-update current-time)))

(defn handle-player-input [player input]
  (let [radians (Math/toRadians (:angle player))
        cos-val (Math/cos radians)
        sin-val (Math/sin radians)]
    (cond-> player
            (:up input)
            (-> (update :velocity-x + (* acceleration cos-val))
                (update :velocity-y + (* acceleration sin-val)))

            (:down input)
            (-> (update :velocity-x - (* acceleration 0.3 cos-val))
                (update :velocity-y - (* acceleration 0.3 sin-val)))

            (:left input)
            (update :angle - rotation-speed)

            (:right input)
            (update :angle + rotation-speed))))

(defn limit-speed [player]
  (let [current-speed (Math/sqrt (+ (Math/pow (:velocity-x player) 2)
                                    (Math/pow (:velocity-y player) 2)))
        scale (if (> current-speed max-speed) (/ max-speed current-speed) 1)]
    (-> player
        (update :velocity-x * scale)
        (update :velocity-y * scale))))

(defn check-boundaries [player]
  (-> player
      (update :x #(max 20 (min 780 %)))
      (update :y #(max 20 (min 580 %)))))

(defn apply-physics [player]
  (-> player
      update-player-physics
      check-boundaries
      limit-speed))