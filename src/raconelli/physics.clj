(ns raconelli.physics
  (:require [clojure.java.io :as io]
            [raconelli.game-state :as game-state]))

;; Константы физики
(def max-speed 5)
(def acceleration 0.4)
(def friction 0.988)
(def rotation-speed 5)
(def push-force 1.5)

;; Контрольные точки [x1 x2 y1 y2]
(def checkpoints
  [{:id 0 :red 255 :green 0 :blue 0 :type :start-finish}
   {:id 1 :red 0 :green 255 :blue 0 :type :checkpoint}
   {:id 2 :red 0 :green 0 :blue 255 :type :checkpoint}
   {:id 3 :red 255 :green 255 :blue 0 :type :checkpoint}])

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
  (let [color (get-pixel-color track-mask (int x) (int y))]
    (boolean (or (and color
                      (= (:red color) 0)
                      (= (:green color) 0)
                      (= (:blue color) 0))
                 (and color
                      (= (:red color) 255)
                      (= (:green color) 0)
                      (= (:blue color) 0))
                 (and color
                      (= (:red color) 0)
                      (= (:green color) 255)
                      (= (:blue color) 0))
                 (and color
                      (= (:red color) 0)
                      (= (:green color) 0)
                      (= (:blue color) 255))
                 (and color
                      (= (:red color) 255)
                      (= (:green color) 255)
                      (= (:blue color) 0))))))

;; Размеры машины
(def car-half-width 10)
(def car-half-height 20)

;; Проверка столкновения
(defn check-collision [x y angle]
  (let [radians (Math/toRadians angle)
        cos-val (Math/cos radians)
        sin-val (Math/sin radians)

        corners [[(- car-half-width) (- car-half-height)]
                 [(- car-half-width) car-half-height]
                 [car-half-width (- car-half-height)]
                 [car-half-width car-half-height]]

        world-corners (map (fn [[local-x local-y]]
                             [(+ x (- (* local-x cos-val) (* local-y sin-val)))
                              (+ y (+ (* local-x sin-val) (* local-y cos-val)))])
                           corners)

        corner-status (map (fn [[wx wy]]
                             {:x wx :y wy :on-track? (is-on-track? wx wy)})
                           world-corners)

        all-on-track? (every? :on-track? corner-status)]

    {:collision? (not all-on-track?)
     :corners corner-status}))

;; Проверка находится ли машина в зоне контрольной точки
(defn in-checkpoint? [x y checkpoint]
  (let [color (get-pixel-color track-mask (int x) (int y))]
    (boolean (or (and color
                      (= (:red color) (:red checkpoint))
                      (= (:green color) (:green checkpoint))
                      (= (:blue color) (:blue checkpoint)))
                 ))))

;; Функция для отладки - логирование прохождения чекпоинтов
;; Обновленная функция для отладки - логирование прохождения чекпоинтов
(defn log-checkpoint-pass [player-id checkpoint-id game-time is-finish? old-best-time new-best-time]
  (println "=== CHECKPOINT DEBUG ===")
  (println "Игрок:" player-id)
  (println "Чекпоинт:" checkpoint-id (if is-finish? "(ФИНИШ)" "(обычный)"))
  (println "Игровое время:" game-time "мс")
  (when is-finish?
    (println "Время круга:" new-best-time "мс")
    (println "Старое лучшее время:" (if (zero? old-best-time) "не установлено" (str old-best-time " мс")))
    (if (and (not (zero? old-best-time)) (< new-best-time old-best-time))
      (println "✅ НОВЫЙ РЕКОРД! Улучшение на" (- old-best-time new-best-time) "мс")
      (if (zero? old-best-time)
        (println "⏱️  Первый круг:" new-best-time "мс")
        (println "⏱️  Текущий круг:" new-best-time "мс"))))
  (println "========================"))

;; Упрощенная и исправленная обработка контрольных точек
;; Исправленная обработка контрольных точек с правильным расчетом времени круга
;; Исправленная обработка контрольных точек с правильным расчетом времени круга
;; Исправленная обработка контрольных точек с защитой от повторной активации
(defn handle-checkpoints [player new-x new-y]
  (let [game-time (:game-time (game-state/get-game-state))
        last-checkpoint (:last-checkpoint player -1)
        ;; Исправляем вычисление следующего чекпоинта
        next-checkpoint-index (if (= last-checkpoint -1)
                                0  ; первый чекпоинт после старта
                                (mod (inc last-checkpoint) (count checkpoints)))
        checkpoint (when (and (>= next-checkpoint-index 0)
                              (< next-checkpoint-index (count checkpoints)))
                     (nth checkpoints next-checkpoint-index))]


    ;; Проверяем, что checkpoint существует и игрок в его зоне
    (if (and checkpoint (in-checkpoint? new-x new-y checkpoint))
      (let [is-finish? (= (:id checkpoint) 0)
            current-time game-time
            last-finish-time (:last-finish-time player 0)]

        (println "=== АКТИВАЦИЯ ЧЕКПОИНТА " (:id checkpoint) " ===")

        ;; Логируем прохождение чекпоинта
        (log-checkpoint-pass (:id player)
                             checkpoint
                             game-time
                             is-finish?
                             (:best-time player 0)
                             (if is-finish?
                               (if (zero? last-finish-time)
                                 0
                                 (- current-time last-finish-time))
                               (:best-time player 0)))

        (if is-finish?
          ;; Завершение круга
          (if (zero? last-finish-time)
            ;; Первый проход финиша
            (do
              (println "Первый проход финиша - устанавливаем last-finish-time")
              (assoc player
                :last-checkpoint 0
                :last-finish-time current-time))
            ;; Последующие проходы финиша
            (let [lap-time (- current-time last-finish-time)
                  best-time (:best-time player 0)]
              (println "Рассчитываем время круга:" lap-time "мс")
              (if (or (zero? best-time) (< lap-time best-time))
                ;; Новый рекорд
                (assoc player
                  :last-checkpoint 0
                  :last-finish-time current-time
                  :best-time lap-time)
                ;; Обычное завершение круга
                (assoc player
                  :last-checkpoint 0
                  :last-finish-time current-time))))
          ;; Обычный чекпоинт
          (assoc player
            :last-checkpoint next-checkpoint-index)))
      player)))
;; Поиск безопасного направления для отталкивания
(defn find-safe-escape-direction [player collision-data old-x old-y]
  (let [angle (:angle player)
        corners (:corners collision-data)

        off-track-corners (filter (comp not :on-track?) corners)
        on-track-corners (filter :on-track? corners)]

    (cond
      (seq on-track-corners)
      (let [avg-x (/ (reduce + (map :x on-track-corners)) (count on-track-corners))
            avg-y (/ (reduce + (map :y on-track-corners)) (count on-track-corners))
            dx (- avg-x old-x)
            dy (- avg-y old-y)
            dist (Math/sqrt (+ (* dx dx) (* dy dy)))]
        (if (zero? dist)
          [(Math/cos (Math/toRadians angle)) (Math/sin (Math/toRadians angle))]
          [(/ dx dist) (/ dy dist)]))

      (seq off-track-corners)
      (let [avg-x (/ (reduce + (map :x off-track-corners)) (count off-track-corners))
            avg-y (/ (reduce + (map :y off-track-corners)) (count off-track-corners))
            dx (- old-x avg-x)
            dy (- old-y avg-y)
            dist (Math/sqrt (+ (* dx dx) (* dy dy)))]
        (if (zero? dist)
          [(Math/cos (Math/toRadians (+ angle 180))) (Math/sin (Math/toRadians (+ angle 180)))]
          [(/ dx dist) (/ dy dist)]))

      :else
      [(Math/cos (Math/toRadians (+ angle 180))) (Math/sin (Math/toRadians (+ angle 180)))])))

;; Ограничение скорости
(defn limit-speed [player]
  (let [current-speed (:speed player)
        abs-speed (if (neg? current-speed) (- current-speed) current-speed)
        scale (if (> abs-speed max-speed) (/ max-speed abs-speed) 1)]
    (update player :speed * scale)))

;; Умная обработка столкновений
(defn handle-collision [player new-x new-y]
  (let [old-x (:x player)
        old-y (:y player)
        current-speed (:speed player)
        angle (:angle player)
        collision-data (check-collision new-x new-y angle)
        collision? (:collision? collision-data)]

    (if (and collision? (not (zero? current-speed)))
      (let [[push-x push-y] (find-safe-escape-direction player collision-data old-x old-y)
            base-force (* push-force (if (neg? current-speed) (- current-speed) current-speed))
            final-pos (loop [attempts 5
                             force-mult 1.0
                             best-pos {:x old-x :y old-y :speed 0}]
                        (if (zero? attempts)
                          best-pos
                          (let [try-x (+ old-x (* push-x base-force force-mult))
                                try-y (+ old-y (* push-y base-force force-mult))
                                try-collision (check-collision try-x try-y angle)]
                            (if (:collision? try-collision)
                              (recur (dec attempts) (* force-mult 1.5) best-pos)
                              {:x try-x :y try-y :speed 0}))))]

        ;(-> player
        ;    (assoc :x (:x final-pos))
        ;    (assoc :y (:y final-pos))
        ;    (assoc :speed (:speed final-pos))
        ;    (assoc :collision-flag true))
        (let [damage 10    ;; сколько HP снимаем за удар
              new-hp (max 0 (- (:hp player) damage))]
          (-> player
              (assoc :x (:x final-pos))
              (assoc :y (:y final-pos))
              (assoc :speed (:speed final-pos))
              (assoc :collision-flag true)
              (assoc :hp new-hp)))
        )

      (dissoc player :collision-flag))))

;; Улучшенное обновление физики
(defn update-player-physics [player]
  (let [current-time (System/currentTimeMillis)
        delta-time (max 0.016 (/ (- current-time (:last-update player)) 1000.0))
        limited-player (limit-speed player)
        current-speed (:speed limited-player)
        angle (:angle limited-player)
        radians (Math/toRadians angle)
        cos-val (Math/cos radians)
        sin-val (Math/sin radians)
        new-speed (* current-speed friction)
        new-x (+ (:x limited-player) (* new-speed cos-val delta-time 60))
        new-y (+ (:y limited-player) (* new-speed sin-val delta-time 60))]

    ;; Обрабатываем столкновение
    (let [player-after-collision (handle-collision limited-player new-x new-y)
          player-after-checkpoints (handle-checkpoints player-after-collision new-x new-y)]

      (if (and (= limited-player player-after-collision)
               (not (:collision-flag player)))
        ;; Без столкновений - используем player-after-checkpoints!
        (assoc player-after-checkpoints  ; ← ИЗМЕНИЛОСЬ ЗДЕСЬ
          :x new-x
          :y new-y
          :speed new-speed
          :last-update current-time)
        ;; Со столкновениями
        (-> player-after-checkpoints
            (update :speed * 0.9)
            (assoc :last-update current-time))))))

;; Обработка ввода
(defn handle-player-input [player input]
  (let [base-player (cond-> player
                            (:up input) (update :speed + acceleration)
                            (:down input) (update :speed - (* acceleration 0.5))
                            (:left input) (update :angle - rotation-speed)
                            (:right input) (update :angle + rotation-speed))

        final-player (if (:collision-flag base-player)
                       (-> base-player
                           (update :speed #(max -2 (min 2 %)))
                           (update :angle #(mod % 360)))
                       base-player)]

    final-player))

;; Проверка границ
(defn check-boundaries [player]
  (-> player
      (update :x #(max 20 (min 780 %)))
      (update :y #(max 20 (min 580 %)))))

;; Главная функция
(defn apply-physics [player]
  (-> player
      update-player-physics
      check-boundaries
      limit-speed))
