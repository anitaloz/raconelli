(ns raconelli.physics
  (:require [clojure.java.io :as io]))

;; Константы физики
(def max-speed 6)
(def acceleration 0.3)
(def friction 0.988)
(def rotation-speed 5)
(def push-force 2.0)

(def track-mask (javax.imageio.ImageIO/read (io/file "resources/public/mask.png")))

;; работа с маской трека
(defn get-pixel-color [img x y]
  (try
    (when (and (>= x 0) (>= y 0) (< x (.getWidth img)) (< y (.getHeight img)))
      (let [rgb (.getRGB img x y)]
        {:red (bit-and (bit-shift-right rgb 16) 0xFF)
         :green (bit-and (bit-shift-right rgb 8) 0xFF)
         :blue (bit-and rgb 0xFF)}))
    (catch Exception e
      nil)))

(defn is-on-track? [x y]
  (let [color (get-pixel-color track-mask x y)]
    (boolean (and color
                  (= (:red color) 0)
                  (= (:green color) 0)
                  (= (:blue color) 0)))))

;; УПРОЩЕННАЯ проверка столкновения - только центр
(defn check-collision [x y]
  (not (is-on-track? (int x) (int y))))

;; ПРОСТАЯ функция отталкивания - отталкиваем в противоположном направлении от движения
(defn find-push-direction [player]
  (let [angle (:angle player)
        radians (Math/toRadians angle)
        ;; Инвертируем направление движения (180 градусов)
        push-angle (+ angle 180)
        push-radians (Math/toRadians push-angle)]
    [(Math/cos push-radians) (Math/sin push-radians)]))

;; Основная логика отталкивания
(defn handle-collision [player new-x new-y]
  (let [
        current-speed (:speed player)
        collision? (check-collision new-x new-y)]

    (if (and collision? (> current-speed 0))
      (do
        ;; Находим направление отталкивания (противоположное движению)
        (let [[push-x push-y] (find-push-direction player)
              force (* push-force current-speed)]


          ;; Отталкиваем назад по направлению движения
          (-> player
              (update :x + (* push-x force))
              (update :y + (* push-y force))
              (assoc :speed 0))))
      player)))

;; обновление физики игрока
(defn update-player-physics [player]
  (let [current-time (System/currentTimeMillis)
        radians (Math/toRadians (:angle player))
        cos-val (Math/cos radians)
        sin-val (Math/sin radians)
        delta-time (max 0.016 (/ (- current-time (:last-update player)) 1000.0))
        new-speed (* (:speed player) friction)

        ;; Вычисляем новую позицию
        new-x (+ (:x player) (* new-speed cos-val delta-time 60))
        new-y (+ (:y player) (* new-speed sin-val delta-time 60))]

    ;; Сначала проверяем столкновение новой позиции
    (let [player-after-collision (handle-collision player new-x new-y)]

      (if (and (= player player-after-collision) ;; Не было столкновения
               (is-on-track? (int new-x) (int new-y))) ;; И можно двигаться
        ;; Можно двигаться
        (assoc player
          :x new-x
          :y new-y
          :speed new-speed
          :last-update current-time)
        ;; Было столкновение
        (assoc player-after-collision
          :last-update current-time)))))

;; обработка ввода игрока
(defn handle-player-input [player input]
  (cond-> player
          (:up input) (update :speed + acceleration)
          (:down input) (update :speed - (* acceleration 0.5))
          (:left input) (update :angle - rotation-speed)
          (:right input) (update :angle + rotation-speed)))

;; ограничение максимальной скорости
(defn limit-speed [player]
  (let [current-speed (:speed player)
        scale (if (> current-speed max-speed) (/ max-speed current-speed) 1)]
    (update player :speed * scale)))

;; проверка границ игрового поля
(defn check-boundaries [player]
  (-> player
      (update :x #(max 20 (min 800 %)))
      (update :y #(max 20 (min 600 %)))))

;; главная функция применения физики
(defn apply-physics [player]
  (-> player
      update-player-physics
      check-boundaries
      limit-speed))