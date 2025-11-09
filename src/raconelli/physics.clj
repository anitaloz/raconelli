(ns raconelli.physics)

;; Константы физики
(def max-speed 5)                  ; максимальная скорость
(def acceleration 0.2)             ; ускорение
(def friction 0.95)                ; трение (замедление)
(def rotation-speed 4)             ; скорость поворота


;; обновление физики игрока
(defn update-player-physics [player]
  (let [current-time (System/currentTimeMillis)
        ;; вычисляем разницу во времени с последнего обновления
        ;; max (ловит даже на парковке) гарантирует, что delta-time не будет отрицательной
        delta-time (max 0.016 (/ (- current-time (:last-update player)) 1000.0))

        ;; применяем трение к скорости
        new-vx (* (:velocity-x player) friction)
        new-vy (* (:velocity-y player) friction)

        ;; вычисляем новую позицию с учетом скорости и времени
        ;; умножение на 60 нормализует движение к 60 фпс
        new-x (+ (:x player) (* new-vx delta-time 60))
        new-y (+ (:y player) (* new-vy delta-time 60))]

    ;; Обновляем состояние игрока
    (assoc player
      :x new-x
      :y new-y
      :velocity-x new-vx
      :velocity-y new-vy
      :last-update current-time)))

;; обработка ввода игрока
(defn handle-player-input [player input]
  ;; преобразуем угол в радианы для математических вычислений
  (let [radians (Math/toRadians (:angle player))
        cos-val (Math/cos radians)
        sin-val (Math/sin radians)]

    ;; cond-> последовательно применяет преобразования к игроку
    ;; cond это аналог if-else if-else, все функции применяются к player'у
    (cond-> player
            ;; движение вперед (W/Up)
            (:up input)
            (-> (update :velocity-x + (* acceleration cos-val))
                (update :velocity-y + (* acceleration sin-val)))

            ;; движение назад (S/Down) - медленнее чем вперед
            (:down input)
            (-> (update :velocity-x - (* acceleration 0.3 cos-val))
                (update :velocity-y - (* acceleration 0.3 sin-val)))

            ;; поворот влево (A/Left)
            (:left input)
            (update :angle - rotation-speed) ; уменьшаем угол

            ;; поворот вправо (D/Right)
            (:right input)
            (update :angle + rotation-speed)))) ; увеличиваем угол

;; ограничение максимальной скорости
(defn limit-speed [player]
  ;; вычисляем текущую скорость (теорема Пифагора)
  (let [current-speed (Math/sqrt (+ (Math/pow (:velocity-x player) 2)
                                    (Math/pow (:velocity-y player) 2)))
        ;; коэффициент масштабирования если превышена максимальная скорость
        ;; если кьюррентспид не превышает максимальную то коэфф 1 (очевидно)
        scale (if (> current-speed max-speed) (/ max-speed current-speed) 1)]
    ;; апдейтим компоненты скорости (т.е. ограничиваем если нужно)
    (-> player
        (update :velocity-x * scale)
        (update :velocity-y * scale))))

;; проверка границ игрового поля
(defn check-boundaries [player]
  (-> player
      ;; ограничиваем X координату между 20 и 780
      (update :x #(max 20 (min 780 %)))
      ;; ограничиваем Y координату между 20 и 580
      (update :y #(max 20 (min 580 %)))))

;; главная функция применения физики
(defn apply-physics [player]
  ;; -> передает игрока через цепочку физических преобразований (все функции выше)
  (-> player
      update-player-physics
      check-boundaries
      limit-speed))