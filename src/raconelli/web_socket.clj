(ns raconelli.web_socket
  (:require [org.httpkit.server :as server]
            [clojure.data.json :as json]
            [raconelli.game-state :as game-state]
            [raconelli.physics :as physics]))

;; обработчик ввода игрока
(defn handle-player-input [player-id input-data]
  (game-state/update-player! player-id #(physics/handle-player-input % input-data)))

;; создание WebSocket обработчика
(defn create-websocket-handler []
  ;; возвращаем функцию-обработчик для HTTP запросов
  (fn [request]
    ;; with-channel создает WebSocket соединение
    (server/with-channel request channel
                         (println "Client connected")

                         ;; генерируем уникальный ID для игрока
                         (let [player-id (str (java.util.UUID/randomUUID))]
                           ;; добавляем нового игрока в игру
                           (game-state/add-player! player-id channel)

                           ;; обработчик закрытия соединения
                           (server/on-close channel
                                            (fn [status]
                                              (println "Client disconnected:" player-id)
                                              ;; удаляем игрока из игры
                                              (game-state/remove-player! player-id)))

                           ;; обработчик входящих сообщений
                           (server/on-receive channel
                                              (fn [data]
                                                (try
                                                  ;; парсим JSON сообщение
                                                  (let [message (json/read-str data :key-fn keyword)]
                                                    ;; обрабатываем в зависимости от типа сообщения
                                                    (case (:type message)
                                                      "player-input" ;; ввод игрока
                                                      (handle-player-input player-id (:input message))

                                                      "ping"  ;; ping-запрос
                                                      (server/send! channel (json/write-str {:type "pong"}))

                                                      ;; неизвестный тип сообщения
                                                      (println "Unknown message type:" (:type message))))

                                                  ;; обработка ошибок парсинга
                                                  (catch Exception e
                                                    (println "Error processing message from" player-id ":" e)))))))))
