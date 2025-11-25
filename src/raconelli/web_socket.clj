(ns raconelli.web_socket
  (:require [org.httpkit.server :as server]
            [clojure.data.json :as json]
            [raconelli.game-state :as game-state]
            [raconelli.physics :as physics]))

;; обработчик ввода игрока
(defn handle-player-input [player-id input-data]
  ;(println "Handling input for player" player-id ":" input-data)
  (game-state/update-player! player-id #(physics/handle-player-input % input-data)))

;; проверка уникальности ID
;(defn is-id-unique [player-id]
;  (not (contains? (game-state/get-players) player-id)))

;; создание WebSocket обработчика
(defn create-websocket-handler []
  ;; возвращаем функцию-обработчик для HTTP запросов
  ;; request - http запрос от клиента
  (fn [request]
    ;; with-channel создает WebSocket соединение
    (server/with-channel request channel
                         ;; выполняется при успешном создании вебсокета
                         (println "Client connected")

                         ;; volatile! объект для изменяемых значений (для однопоточного доступа)
                         ;; нам подходит потому что вебсокеты обрабатываются в своих потоках (вроде как)
                         (let [player-id (volatile! nil)]

                           ;; обработчик закрытия соединения
                           ;; должен срабатывать если закрыта вкладка/закрыто соединение/отключился интернет
                           (server/on-close channel
                                            (fn [status]
                                              (println "Client disconnected")
                                              ;; удаляем игрока из игры
                                              (when-let [pid @player-id] ;; выполнится если айди не nil
                                                (game-state/remove-player! pid))
                                            )
                           )

                           ;; обработчик входящих сообщений
                           (server/on-receive channel
                                              (fn [data]
                                                (try
                                                  ;; парсим JSON сообщение
                                                  (let [message (json/read-str data :key-fn keyword)]
                                                    ;; обрабатываем в зависимости от типа сообщения
                                                    (case (:type message)

                                                      "player-id" ;; отправка айди с клиента
                                                      ( let [client-id (:playerId message) ;; полученный айди
                                                             existing-players (game-state/get-players)] ;; все игроки на серве

                                                        (println "join attempt:" client-id)

                                                        ;; проверка на уникальность
                                                        (if (contains? existing-players client-id)

                                                          ;; айди не уникальный
                                                          (do (println "id already in use: " client-id)
                                                              (server/send! channel (json/write-str
                                                                   {:type "join-error"
                                                                    :error "Player ID already taken"})))

                                                          ;; айди уникальный
                                                          (do
                                                            (println "id is unique:" client-id)
                                                            (vreset! player-id client-id)
                                                            (game-state/add-player! client-id channel)
                                                            (server/send! channel (json/write-str
                                                                                    {:type "join-success"
                                                                                     :message "Connected successfully"}))
                                                            )
                                                          )

                                                        ; сохраняем айди в общую переменную
                                                       ;(vreset! player-id client-id) ;;  изменяем значение volatile
                                                       ;(println "new id:" client-id)
                                                       ;;; добавление игрока
                                                       ;(game-state/add-player! client-id channel)
                                                      )

                                                      "player-input" ;; ввод игрока
                                                      (handle-player-input (:playerId message) (:input message))

                                                      "ping"  ;; ping-запрос
                                                      (server/send! channel (json/write-str {:type "pong"}))

                                                      ;; неизвестный тип сообщения
                                                      (println "Unknown message type:" (:type message))))

                                                  ;; обработка ошибок парсинга
                                                  (catch Exception e
                                                    (println "Error processing message " e)))))
                         )
    )
  )
)