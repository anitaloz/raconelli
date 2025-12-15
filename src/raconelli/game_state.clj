(ns raconelli.game-state
  (:require [raconelli.players :as players]))

;; глобальное состояние игры с использованием ref для транзакционных обновлений
(def game-state
  (ref {:players {}        ; коллекция игроков {id -> данные игрока}
        :game-time 0       ; игровое время в секундах
        :game-status :playing       ; :playing :finished :restarting
        :winner nil        ;победитель по окончании игры
        :game-duration 500  ; длительность игры в секундах (1 минута)
        :reatart-timer 0;таймер рестарта в секундах
        ;:hp 100
        }))


;; коллекция WebSocket каналов клиентов
(def client-channels (ref {}))

;; получение текущего состояния игры
(defn get-game-state []
  @game-state) ; @ - deref, получение значения из ref

;; Обновление состояния игры
(defn update-game-state! [update-fn]
  (dosync
    (alter game-state update-fn)))    ;; при вызове в .server в качестве аргумента передается функция,
                                      ;; прописанная в update-game-loop и обновляет game-stete

;; добавление нового игрока
(defn add-player! [player-id channel]         ;; передаем айди
  (dosync
    ;; добавляем канал в client-channels
    (alter client-channels assoc player-id channel)         ;; assoc это функция Clojure для добавления или обновления пар ключ-значение
    ;; добавляем игрока в список игроков:players в game-state
    ;; тут через assoc добавляется новое значение в коллекцию players, а через update эта коллекция изменяется как поле в game-state
    (alter game-state update :players assoc player-id (players/create-player player-id))))
;; короче update изменяет текущее значение, а assoc обновляет/добавляет фулл новое

;; удаление игрока
(defn remove-player! [player-id]
  (dosync
    ;; удаляем канал из коллекции
    (alter client-channels dissoc player-id)
    ; удаляем игрока из состояния
    ; same thing: из players через dissoc удаляется значение по ключу player-id, а потом через update обновляется game-sate
    (alter game-state update :players dissoc player-id)))

;; обновление конкретного игрока
(defn update-player! [player-id update-fn]
  (dosync
    ; update-in обновит значение в коллекции players по ключу player-id
    (alter game-state update-in [:players player-id] update-fn)))

;; получение коллекции игроков
(defn get-players []
  (:players @game-state))          ;; @ - это сокращенная запись для deref

;; получение коллекции клиентских каналов
(defn get-client-channels []
  @client-channels)               ;; @ - это сокращенная запись для deref

;; рассылка сообщения всем клиентам
(defn broadcast-to-all! [message-fn]
  ;; douseq - итерация по коллекции
  (doseq [[_ channel] (get-client-channels)]
    (when channel           ; если канал существует
      (try
        ;; применяем функцию сообщения (аргумент этой функции) к каналу
        (message-fn channel)
        ;; обработка ошибок отправки
        (catch Exception e
          (println "Error broadcasting to client:" e))))))

;; Определение победителя
(defn determine-winner []
  (let [players (get-players); получаем всех игроков {id -> player-data}
        ;; filter оставляет только игроков, у которых best-time > 0
        players-with-laps (filter #(pos? (:best-time (second %))) players)]
    (if (empty? players-with-laps) ; если нет игроков с завершенными кругами - победителя нет
      nil
      (->> players-with-laps
           (sort-by (fn [[_ player]] (:best-time player)))
           first; берем первого (с наименьшим best-time)
           key)))); из пары [id player-data] извлекаем ключ (id игрока)

;; Завершение игры и определение победителя
(defn finish-game! []
  (dosync
    (let [winner (determine-winner)]; определяем победителя
      ;; alter атомарно изменяет game-state
      (alter game-state assoc; assoc добавляет/заменяет ключи в map
             :game-status :finished; меняем статус на "завершено"
             :winner winner; сохраняем победителя
             :restart-timer 5) ; устанавливаем таймер рестарта на 30 секунд
      winner))); возвращаем победителя

;; Сброс игры для рестарта (ИСПРАВЛЕННАЯ ВЕРСИЯ)
(defn reset-game! []
  (dosync
    ;; перемещаем существующих игроков на старт
    (let [current-players (:players @game-state); получаем текущих игроков
          ;; Функция для сброса позиции и статистики игрока
          reset-player (fn [player]
                         (-> player
                             (assoc :x 450)           ; Переносим на старт X
                             (assoc :y 100)           ; Переносим на старт Y
                             (assoc :speed 0)         ; Сбрасываем скорость
                             (assoc :angle 0)         ; Сбрасываем угол
                             (assoc :best-time 0)     ; Сбрасываем лучшее время
                             (assoc :last-finish-time 0) ; Сбрасываем время финиша
                             (assoc :last-checkpoint -1) ; Сбрасываем чекпоинты
                             (assoc :last-update (System/currentTimeMillis)))) ; Обновляем время

          ;; Применяем reset-player ко всем игрокам
          reset-players (into {}
                              (map (fn [[id player]];; map применяет функцию к каждой паре [id player]
                                     [id (reset-player player)])
                                   current-players))]

      ;; Атомарно обновляем состояние игры
      (alter game-state assoc
             :players reset-players    ; Игроки с обновленными позициями
             :game-time 0              ; Сбрасываем время
             :game-status :playing     ; Возвращаем статус "играем"
             :winner nil               ; Очищаем победителя
             :restart-timer 0))        ; Сбрасываем таймер рестарта

    (println "Game reset completed! Players moved to start. Players count:"
             (count (:players @game-state)))))

;; Проверка, завершена ли игра
(defn is-game-finished? []
  (= :finished (:game-status @game-state)))

;; Получение оставшегося времени
(defn get-remaining-time []
  (let [state @game-state
        game-time (:game-time state) ; извлекаем текущее игровое время
        duration (:game-duration state)]; извлекаем общую длительность игры
    (max 0 (- duration game-time))));; max гарантирует, что время не будет отрицательным

;; Проверка времени игры и завершение при необходимости
(defn check-game-time! []
  (let [remaining-time (get-remaining-time)]; получаем оставшееся время
    (if (<= remaining-time 0); если время вышло (<= 0)
      (do
        (println "Game time finished! Determining winner...")
        (finish-game!); завершаем игру
        true) ; игра завершена
      false))) ; иначе игра продолжается

;; Проверка таймера рестарта
(defn check-restart-timer! []
  (let [state @game-state
        restart-timer (:restart-timer state)]; извлекаем таймер рестарта
    (when (and (= :finished (:game-status state))
               (> restart-timer 0))
      ;; Обновляем таймер рестарта
      (dosync
        (alter game-state update :restart-timer - 0.016))

      (let [new-restart-timer (:restart-timer @game-state)]; получаем новый таймер
        (when (<= new-restart-timer 0); если таймер достиг 0 или меньше
          (println "Restarting game...")
          (reset-game!); перезапускаем игру
          true))))) ; возвращаем true если игра перезапущена

;; получение игрока по айди
(defn get-player [player-id]
  (get-in @game-state [:players player-id]))
