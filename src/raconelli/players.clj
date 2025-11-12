(ns raconelli.players)

(defn create-player [id]
  {:id id            ; уникальный идентификатор игрока
   :x 100            ; начальная позиция по X
   :y 100            ; начальная позиция по Y
   :speed 0
   :angle 0  ; угол поворота игрока
   :hp 10
   :car (rand-nth ["red_bull", "mercedes", "ferrari", "zauber"])
   :color (rand-nth ["#FF0000" "#00FF00" "#0000FF" "#FFFF00" "#FF00FF" "#00FFFF"])
   :last-update (System/currentTimeMillis)  ; время последнего обновления (в мс)
   :name (str "Player-" (subs id 0 6))})    ; имя игрока = "Player-" + первые 6 символов ID

(defn get-player-color [player-id players]
  "Функция получения цвета игрока"
  ;; get-in получает значение по пути [:players player-id :color]
  (get-in players [player-id :color] "#FFFFFF")) ;; если игрок не найден, возвращает белый цвет "#FFFFFF"

(defn get-player-position [player-id players]
  "Функция получения позиции игрока"
  (let [player (get players player-id)] ; Получаем игрока по ID из players
    (when player  ; Если игрок существует
      {:x (:x player)             ; Возвращаем X координату
       :y (:y player)             ; Возвращаем Y координату
       :angle (:angle player)}))) ; Возвращаем угол поворота

(defn get-all-players-positions [players]
  "Функция получения позиций всех игроков"
  (into {} (map (fn [[id player]]     ;; into {} - преобразует последовательность в map
                  [id (select-keys player [:x :y :angle :color])])  ;; Для каждого [id player] создаем пару [id {данные игрока}]
                players)))