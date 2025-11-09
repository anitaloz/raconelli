(ns raconelli.game-state
  (:require [raconelli.players :as players]))

;; глобальное состояние игры с использованием ref для транзакционных обновлений
(def game-state
  (ref {:players {}        ; коллекция игроков {id -> данные игрока}
        :game-time 0}))    ; игровое время

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