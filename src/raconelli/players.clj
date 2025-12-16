(ns raconelli.players)
(def car-types ["zauber" "mercedes" "ferrari" "red_bull", "pickme", "purple", "skull_car"])
;; Константы шин
(def tyre-types {
                 :soft {
                        :name                    "Soft"
                        :max-speed-multiplier    1.1      ; -30% скорости
                        :acceleration-multiplier 1.4    ; -40% ускорения
                        :rotation-multiplier     1.15        ; -30% поворота
                        :friction-multiplier     1.01        ; +30% сцепления (лучшее сцепление)
                        :hp-bonus                20                  ; +120 HP
                        :damage-reduction        0.3          ; 80% защиты (получают меньше урона)
                        :color                   "#FF0000"               ; Белый цвет
                        }
                 :medium {; Медиумы - сбалансированные (универсал)
                          :name                    "Medium"
                          :max-speed-multiplier    1.0      ; стандартная скорость
                          :acceleration-multiplier 1.0    ; стандартное ускорение
                          :rotation-multiplier     1.0        ; стандартный поворот
                          :friction-multiplier     1.0        ; стандартное сцепление
                          :hp-bonus                50                   ; +50 HP
                          :damage-reduction        0.5          ; 50% защиты
                          :color                   "#FFDB02"               ; Желтый цвет
                          }
                 :hard {
                        :name                    "Hard"
                        :max-speed-multiplier    0.92      ; +60% скорости
                        :acceleration-multiplier 0.9    ; +50% ускорения
                        :rotation-multiplier     0.9        ; +30% поворота
                        :friction-multiplier     0.95        ; -20% сцепления (более скользкие)
                        :hp-bonus                100                   ; +20 HP
                        :damage-reduction        0.8         ; 20% защиты (получают больше урона)
                        :color                   "#FFFFFF"               ; Красный цвет
                        }
                 })
;; Получение характеристик шин
(defn get-tyre-stats [tyre-type]
  (let [stats (get tyre-types tyre-type (get tyre-types :medium))]
    (println "Getting tyre stats for" tyre-type "=>" (:name stats))
    stats)) ; по умолчанию медиумы

(defn create-player [id]
  {:id id
   :x 450  ; Начинаем ЛЕВЕЕ чекпоинта 0
   :y 100
   :speed 0
   :angle 0
   :base-hp 100   ; Базовое HP для всех игроков
   :hp 100        ; здоровье ДЛЯ ШИН
   :maxHp 200     ; максимум здоровья
   :damage 10    ; сколько хп снимается за удар со стеной ДЛЯ ШИН
   ;:deadUntil 0
   ;:deadMessageShown false
   ;:canMove true
   ;:deathTime nil
   ;:invincibleUntil  0 ; неуязвимость после столкновения
   ;:blink 0
   :car (rand-nth car-types) ;mclaren,haas,alpine,aston martin,williams
   ;:color (rand-nth ["#FF0000" "#00FF00" "#0000FF" "#FFFF00" "#FF00FF" "#00FFFF"])
   :last-update (System/currentTimeMillis)
   :name (str "Racer-" id)
   :best-time 0
   :last-finish-time 0
   :last-checkpoint -1
   :tyres :hard
   :tyre-stats (get-tyre-stats :medium) ; Характеристики шин
   })  ; Добавляем отслеживание последнего обработанного чекпоинта; добавили начальный чекпоинт

;; смена цвета машинки
(defn change-car [color]

  )
;(defn get-player-color [player-id players]
;  "Функция получения цвета игрока"
;  ;; get-in получает значение по пути [:players player-id :color]
;  (get-in players [player-id :color] "#FFFFFF")) ;; если игрок не найден, возвращает белый цвет "#FFFFFF"

(defn get-player-position [player-id players]
  "Функция получения позиции игрока"
  (let [player (get players player-id)] ; Получаем игрока по ID из players
    (when player  ; Если игрок существует
      {:x (:x player)             ; Возвращаем X координату
       :y (:y player)             ; Возвращаем Y координату
       :angle (:angle player)}))) ; Возвращаем угол поворота

;(defn get-all-players-positions [players]
;  "Функция получения позиций всех игроков"
;  (into {} (map (fn [[id player]]     ;; into {} - преобразует последовательность в map
;                  [id (select-keys player [:x :y :angle :color])])  ;; Для каждого [id player] создаем пару [id {данные игрока}]
;                players)))


;; функция для переключения машины
(defn next-car [current]
  (let [idx (.indexOf car-types current)
        next-idx (mod (inc idx) (count car-types))]
    (nth car-types next-idx)))

;; функция для смены шин
(defn change-tyres [player tyre-type]
  (println "=== CHANGING TYRES ===")
  (println "Player ID:" (:id player))
  (println "Old tyres:" (:tyres player))
  (println "New tyres:" tyre-type)

  (let [tyre-stats (get-tyre-stats tyre-type)
        base-hp (:base-hp player 100)
        hp-bonus (:hp-bonus tyre-stats 0)
        new-max-hp (+ base-hp hp-bonus)]

    (println "Tyre stats:" tyre-stats)
    (println "Base HP:" base-hp)
    (println "HP bonus:" hp-bonus)
    (println "New max HP:" new-max-hp)
    (println "Damage reduction:" (:damage-reduction tyre-stats))

    (-> player
        (assoc :tyres tyre-type)
        (assoc :tyre-stats tyre-stats)
        (assoc :max-hp new-max-hp)
        ;; Восстанавливаем HP при смене шин
        (assoc :hp new-max-hp)
        ;; Обновляем урон в зависимости от шин
        (assoc :damage (int (* 10 ; базовый урон
                               (- 1.0 (:damage-reduction tyre-stats 0.5))))))))

