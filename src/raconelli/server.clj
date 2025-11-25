(ns raconelli.server
  (:require [org.httpkit.server :as server]       ; HTTP сервер
            [compojure.core :refer :all]          ; Маршрутизация
            [compojure.route :as route]           ; Статические маршруты
            [clojure.data.json :as json]          ; JSON парсинг
            [raconelli.game-state :as game-state] ; Игровое состояние
            [raconelli.web_socket :as websocket]  ; WebSocket обработка
            [raconelli.physics :as physics])      ; Физика игры
  (:gen-class))

(defn update-game-loop []
  "Цикл обновления игрового состояния"
  (game-state/update-game-state!
    (fn [state]  ; state - текущее состояние игры
      (if (game-state/is-game-finished?)
        state ; если игра завершена, не обновляем физику
        (-> state  ;; -> (thread-first) передает state через цепочку функций
            ;; Обновляем players
            (update :players             ;; update это функция из стандартной библиотеки Clojure (update map key update-fn & args)
                    (fn [players]
                      (into {} ;; into {} преобразует последовательность в map
                            (map (fn [[id player]] ;; [id (обновленный игрок)] - применяем физику к игроку
                                   [id (physics/apply-physics player)]))
                            players))
                    )
            (update :game-time + 0.016)))))) ;; Увеличиваем игровое время на 0.016 (примерно 60 FPS)


(defn broadcast-game-state []
  "Рассылка состояния игры всем клиентам"
  (let [state (game-state/get-game-state)]  ; Получаем текущее состояние
    (game-state/broadcast-to-all! ;; Рассылаем всем подключенным клиентам
      (fn [channel]               ; channel - WebSocket канал клиента
        (server/send! channel     ; Отправляем сообщение через канал
                      (json/write-str   ;; Преобразуем данные в JSON строку
                        {:type "game-state"     ; Тип сообщения
                         :state {:players (:players state)     ; Данные игроков
                                 :game-time (:game-time state)
                                 :game-status (:game-status state)
                                 :winner (:winner state)
                                 :remaining-time (game-state/get-remaining-time)
                                 :restart-timer (:restart-timer state)}})))))) ; Игровое время, информация о статусе игры

(defn game-loop []
  "игровой цикл"
  (future                  ;; future запускает код в отдельном потоке
    (println "Starting game loop...")
    ;; Бесконечный цикл
    (loop []
      (Thread/sleep 16) ; Приостановка на 16мс (~60 фпс)
      (try
        ;; Проверяем, не закончилось ли время игры
        (when-not (game-state/is-game-finished?)
          (update-game-loop)         ;; Обновляем состояние игры (функция выше)
          (game-state/check-game-time!)) ;; Проверяем время игры
        ;; Проверяем таймер рестарта (работает даже когда игра завершена)
        ;; вызываем функцию и используем ее результат
        (let [game-restarted? (game-state/check-restart-timer!)]
          (when game-restarted?
            (println "New game started!")))
        (broadcast-game-state)     ;; Рассылаем обновленное состояние (функция выше)
        (catch Exception e
          (println "Error in game loop:" e))) ;; Обработка ошибок в игровом цикле
      (recur)))) ; Продолжаем цикл

;; HTTP routes
(defroutes app-routes
           ;; WebSocket endpoint
           (GET "/ws" [] (websocket/create-websocket-handler)) ;; точка входа (URL) на сервере, которая принимает и обрабатывает WebSocket соединения
           ;; Главная страница HTML из ресурсов
           (GET "/" []
                (slurp (clojure.java.io/resource "public/index.html"))) ;; slurp читает все содержимое файла в строку и возвращает HTML содержимое
           ;; Endpoint статуса сервера
           (GET "/status" []
                {:status 200
                 :headers {"Content-Type" "application/json"}
                 :body (json/write-str
                         {:players (count (game-state/get-players))                ; Количество игроков
                          :game-time (:game-time (game-state/get-game-state)); Игровое время
                          :game-status (:game-status (game-state/get-game-state))
                          :remaining-time (game-state/get-remaining-time)
                          :restart-timer (:restart-timer (game-state/get-game-state))})})
           ;; Статические ресурсы (CSS, JS, изображения)
           (route/resources "/")
           ;; Обработчик для неизвестных маршрутов
           (route/not-found "Not Found")
           )

(defn -main [& args]
  "Главная функция запуска сервера"
  ;; Получаем порт из переменной окружения или используем 8080 по умолчанию
  (let [port (or (System/getenv "PORT") 8080)]
    (println "Starting racing game server on port" port)
    ;; старт игрового цикла
    (game-loop)
    ;; старт HTTP сервера с маршрутами из app-routes
    (server/run-server #'app-routes {:port (Integer. port)})))
;; с помощью # передается ссылка на переменную чтобы сервер отслеживал текующее значение

