(ns raconelli.players)
(def car-types ["zauber" "mercedes" "ferrari" "red_bull", "pickme"])
(defn create-player [id]
  {:id id
   :x 450  ; Начинаем ЛЕВЕЕ чекпоинта 0
   :y 100
   :speed 0
   :angle 0
   :hp 10
   :car (rand-nth car-types) ;mclaren,haas,alpine,aston martin,williams
   ;:color (rand-nth ["#FF0000" "#00FF00" "#0000FF" "#FFFF00" "#FF00FF" "#00FFFF"])
   :last-update (System/currentTimeMillis)
   :name (str "Racer-" id)
   :best-time 0
   :last-finish-time 0
   :last-checkpoint -1
   :tyres nil
   })  ; Добавляем отслеживание последнего обработанного чекпоинта; добавили начальный чекпоинт ; имя игрока = "Player-" + первые 6 символов ID

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