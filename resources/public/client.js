//// ГЛАВНЫЙ КЛАСС КЛИЕНТА
class RacingGameClient {

    constructor() {
        this.playerId = (Math.random(100)).toString();
        this.ws = null;    // вебсокет соединение
        this.gameState = { players: {}, gameTime: 0 };  // состояние игры
        this.keys = {};    // состояние нажатых клавиш
        this.canvas = document.getElementById('game-canvas');      // html канвас элемент
        this.ctx = this.canvas.getContext('2d');                   // 2д контекст для рисования на канвас
        this.carImages = new Map()
        this.trackImage = null
        this.onTrack = true
        this.isConnected = false;

        // вызов методов инициализации (все функции ниже)
        this.setupEventListeners();  // настройка обработчиков событий

//        this.connect();              // подключение к серверу
//        this.gameLoop();             // запуск игрового цикла

        // Предзагрузка изображений машинок
        this.preloadCarImages();
    }

    preloadCarImages() {
        var trackImg=new Image()
        trackImg.src=`track.jpg`
        this.trackImage=trackImg
        const carTypes = ['zauber', 'mercedes', 'ferrari', 'red_bull', "pickme"]; // Добавьте нужные типы машинок

        carTypes.forEach(carType => {
            const img = new Image();
            img.src = `/cars/${carType}.png`;
            img.onload = () => {
                console.log(`Car image ${carType} loaded`);
                this.carImages.set(carType, img);
            };
            img.onerror = () => {
                console.error(`Failed to load car image: ${carType}`);
            };
        });
    }

    // настройка обработчиков событий клавиатуры
    setupEventListeners() {
        // обработчик нажатия клавиш
        document.addEventListener('keydown', (e) => {
            this.keys[e.key.toLowerCase()] = true;   // true = нажата (состояние клавиши)

            // console.log("check");

            // отправляем текущий ввод на сервер
            this.sendInput();
        });

        // обработчик отпускания клавиш
        document.addEventListener('keyup', (e) => {
            this.keys[e.key.toLowerCase()] = false;   // false = не нажата (состояние клавиши)

            // отправляем текущий ввод на сервер
            this.sendInput();
        });

        // получение и отправка айди
        const idForm = document.getElementById('id-input-btn');
        idForm.addEventListener('click', () => {

            //idForm.disabled = true;

            const idInput = document.getElementById('id-input').value;

            console.log('id:', idInput);

            const number = Number(idInput);

            if (idInput === '' ||  isNaN(number) || !Number.isInteger(number) || number < 1 || number > 99){
                console.log("id check error");
                //idForm.disabled = false;
            }
            else {
                console.log("id check success");
                this.playerId = idInput;
                this.connect();
            }

        });

        const changeCar = document.getElementById('change-car-btn');
        changeCar.addEventListener('click', ()=>{
            if (this.ws && this.ws.readyState === WebSocket.OPEN) {
                this.ws.send(JSON.stringify({
                    type: 'change-car',   // тип сообщения
                    playerId: this.playerId
                }));
            }
        });


        const changeTyres = document.getElementById('change-tyres-btn');
        changeTyres.addEventListener('click', () =>{
            this.ws.send(JSON.stringify({
                type: 'change-tyres',   // тип сообщения
                playerId: this.playerId,
                tyresType: 'tyretype'
            }));
        });
    }

    // подключение к WebSocket серверу
    connect() {

        // определяем протокол (ws:// или wss://) в зависимости от текущего протокола страницы
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        // формируем URL WebSocket соединения
        const wsUrl = `${protocol}//${window.location.host}/ws`;
        // новое WebSocket соединение
        this.ws = new WebSocket(wsUrl);

        // обработчик успешного подключения
        this.ws.onopen = () => {
            console.log('Connected to server');

            console.log("playerId:",this.playerId);

            // обновляем статус подключения в UI
            document.getElementById('connection-status').textContent = 'Connected';

            console.log("send-id: ", this.playerId);
            // отправляем сообщение на сервер в формате JSON
            this.ws.send(JSON.stringify({
                type: 'player-id',   // тип сообщения
                playerId: this.playerId
            }));
        };

        // обработчик закрытия соединения
        this.ws.onclose = () => {
            console.log('Disconnected from server');

            // обновляем статус подключения в UI
            document.getElementById('connection-status').textContent = 'Disconnected';

            // попытка переподключения через 3 секунды
            // setTimeout(() => this.connect(), 3000);
        };

        // обработчик входящих сообщений от сервера
        this.ws.onmessage = (event) => {
            try {
                // переводим в джс объект JSON сообщение от сервера
                const message = JSON.parse(event.data);

                // если сообщение содержит состояние игры
                if (message.type === 'game-state') {
                    // обновляем локальное состояние игры
                    this.gameState = message.state;
                    // обновляем UI
                    this.updateUI();
                }
                else if ( message.type === 'join-success') {
                    console.log('Join successful:', message.message);
                    this.isConnected = true;

                    document.getElementById('user-input-id').style.display = "none";

                    this.gameLoop();

                }
                else if ( message.type === 'join-error') {
                    console.error("join error:", message.error)
                    this.isConnected = false;

                    this.ws.close();
                }
            } catch (e) {
                console.error('Error parsing message:', e);
            }
        };

        // обработчик ошибок WebSocket
        this.ws.onerror = (error) => {
            console.error('WebSocket error:', error);
        };
    }

    // отправка состояния ввода игрока на сервер
    sendInput() {
        // проверяем что соединение открыто и готово к отправке
        if (this.ws && this.ws.readyState === WebSocket.OPEN) {
            const player = this.gameState.players[this.playerId];

            if (player.hp !== 0) {
                console.log("player hp: ", player.hp);
                const input = {
                    up: this.keys['w'] || false,
                    down: this.keys['s'] || false,
                    left: this.keys['a'] || false,
                    right: this.keys['d'] || false
                };
                // отправляем сообщение на сервер в формате JSON
                this.ws.send(JSON.stringify({
                    type: 'player-input',   // тип сообщения
                    playerId: this.playerId,
                    input: input
                }));
                // обработка в вебсокет хэндлере в r.web_socket
            }
        }
    }

    drawCarWithImage(ctx, player) {
        var carType = player.car || 'ferrari'

        if (this.carImages.has(carType)) {
            const carImage = this.carImages.get(carType);
            ctx.save();
            ctx.translate(player.x, player.y);
            ctx.rotate(player.angle * Math.PI / 180);

            const carWidth = 40;
            const carHeight = 20;
            ctx.drawImage(carImage, -carWidth/2, -carHeight/2, carWidth, carHeight);

            ctx.restore();
        } else {
            // Если изображение еще не загружено, используем fallback
            this.drawCar(ctx, player);

            // Пытаемся загрузить изображение если еще не начали
            if (!this.carImages.has(carType)) {
                const img = new Image();
                img.src = `/cars/${carType}.png`;
                img.onload = () => {
                    console.log(`Car image ${carType} loaded`);
                    this.carImages.set(carType, img);
                };
                img.onerror = () => {
                    console.error(`Failed to load car image: ${carType}`);
                };
            }
        }
    }

    // отрисовка машины игрока на canvas
    drawCar(ctx, player) {
        // сохраняем текущее состояние контекста
        ctx.save();
        // перемещаем начало координат в позицию машины
        ctx.translate(player.x, player.y);
        // поворачиваем контекст на угол машины (перевод из градусов в радианы)
        ctx.rotate(player.angle * Math.PI / 180);

        // отрисовка машинки
        ctx.fillStyle = '#ffffff';
        ctx.fillRect(-15, -8, 30, 16);

        // более подробная отрисовка машинки
        ctx.fillStyle = '#333';
        ctx.fillRect(5, -6, 8, 12);
        ctx.fillRect(-13, -6, 8, 12);

        // отрисовка колес
        ctx.fillStyle = '#222';
        ctx.fillRect(-12, -10, 5, 4);
        ctx.fillRect(-12, 6, 5, 4);
        ctx.fillRect(7, -10, 5, 4);
        ctx.fillRect(7, 6, 5, 4);

        // восстанавливаем состояние контекста (отменяем трансформации)
        ctx.restore();

    }


    drawTrack(ctx) {
        if (this.trackImage && this.trackImage.complete) {
            ctx.drawImage(this.trackImage, 0, 0, this.canvas.width, this.canvas.height);
        } else {
            // fallback, если изображение ещё не загружено
            ctx.fillStyle = "#27ae60";
            ctx.fillRect(0, 0, this.canvas.width, this.canvas.height);
        }
    }

    // основной метод отрисовки игры
    render() {
        // очистка canvas - заливка фоном (зеленый цвет травы)
        this.ctx.fillStyle = '#27ae60';
        this.ctx.fillRect(0, 0, this.canvas.width, this.canvas.height);

        // отрисовка трека (функция выше)
        this.drawTrack(this.ctx);
        // отрисовка всех игроков (для каждого игрока из геймстейта вызывается функция отрисовки)
        // Object.values() преобразует объект players в массив значений
        Object.values(this.gameState.players).forEach(player => {
            // player.maxHp = player.maxHp || 100;
            // player.hp = 100;
            console.log("Drawing player:", player.id, "HP:", player.hp);
            this.drawCarWithImage(this.ctx, player);
            //this.checkBoundaries(this.ctx, player)
            this.updateHP(player); // обновление таблицы HP
        });
    }
// Обновление хп
    updateHP(player) {
        const tbody = document.getElementById('hp-table-body');
        if (!tbody) return;

        let row = document.getElementById(`hp-row-${player.id}`);
        if (!row) {
            // создаем строку для игрока
            row = document.createElement('tr');
            row.id = `hp-row-${player.id}`;

            const nameCell = document.createElement('td');
            nameCell.textContent = `${player.id}`;
            nameCell.style.padding = '4px';
            row.appendChild(nameCell);

            const barCell = document.createElement('td');
            barCell.style.padding = '4px';
            const hpBar = document.createElement('div');
            hpBar.id = `hp-bar-${player.id}`;
            hpBar.style.cssText = "width: 100%; height: 12px; background: linear-gradient(to right, #6fcf97, #56ccf2); border: 1px solid #888; border-radius: 4px;";
            barCell.appendChild(hpBar);
            row.appendChild(barCell);

            tbody.appendChild(row);
        }

        const hpBar = document.getElementById(`hp-bar-${player.id}`);
        if (hpBar && player.hp != null && player.maxHp != null) {
            const percentage = Math.max(0, Math.min(1, player.hp / player.maxHp)) * 100;
            hpBar.style.width = percentage + '%';
        }
    }
    // обновление пользовательского интерфейса (боковая панель)
    updateUI() {
        // получаем массив всех игроков
        const players = Object.values(this.gameState.players);
        document.getElementById('player-count').textContent = players.length;

        // игровое время
        this.updateGameTimeDisplay();

        const gameStatus = this.gameState['game-status'] || 'playing';
        if (gameStatus === 'finished') {
            this.showGameResults();
        } else {
            // Скрываем уведомление о победителе когда игра активна
            this.hideGameResults();
        }

        // обновляем счетчик игроков
        const playersList = document.getElementById('players-list');
        playersList.innerHTML = '';

        // для каждого игрока создаем элемент в списке
        players.forEach(player => {
            const playerElement = document.createElement('div');
            playerElement.className = 'player-item';

            // HTML содержимое элемента игрока:
            // Цветной квадратик с цветом машины
            playerElement.innerHTML = `
                        <div class="player-color" style="background-color: white"></div>
                        Player ${player.id}
                    `;

            // добавляем элемент в список
            playersList.appendChild(playerElement);
        });

        // таблица с игроками
        const tableBody = document.getElementById('players-table-body');
        tableBody.innerHTML = '';

        // сортировка игроков по лучшему времени (по возрастанию)
        const sortedPlayers = players.sort((a, b) => {
            const timeA = a['best-time'] || a.bestTime || 0;
            const timeB = b['best-time'] || b.bestTime || 0;
            return timeA - timeB;
        });

        // строки таблицы
        sortedPlayers.forEach((player, index) => {
            const row = document.createElement('tr');

            // выделяем текущего игрока
            if (player.id === this.playerId) {
                row.style.background = '#0c293e'; // фон для текущего игрока
                row.style.fontWeight = 'bold';
            }

            // форматируем лучшее время
            const bestTime = player['best-time'] || 0;
            const formattedTime = this.formatTime(bestTime);

            row.innerHTML = `
                 <td style="padding: 8px; border: 1px solid #ddd; text-align: center;">
                     ${index + 1}
                 </td>
                 <td style="padding: 8px; border: 1px solid #ddd; text-align: left;">
                     <div style="display: flex; align-items: center; gap: 8px;">
                        <div class="player-color" style="width: 12px; height: 12px; border-radius: 50%; background-color: white"></div>
                         ${player.name}
                         ${player.id === this.playerId ? '<span style="color: #2196F3;">(Вы)</span>' : ''}
                     </div>
                 </td>
                 <td style="padding: 8px; border: 1px solid #ddd; text-align: center; font-family: monospace;">
                    ${formattedTime}
                 </td>
             `;

            tableBody.appendChild(row);
        });
        // this.updateDeadStates();
    }

    formatTime(seconds) {
        if (!seconds || seconds === 0) return 'no time';

        const minutes = Math.floor(seconds / 60);
        const remainingSeconds = seconds % 60;
        const milliseconds = Math.floor((seconds % 1) * 1000);

        return `${minutes.toString().padStart(2, '0')}:${remainingSeconds.toFixed(3).padStart(6, '0')}`;
    }

    updateGameTimeDisplay() {
        const gameTime = this.gameState['game-time']; // время в секундах

        const remainingTime = this.gameState['remaining-time'] || 60;
        const restartTimer = this.gameState['restart-timer'] || 0;
        const gameStatus = this.gameState['game-status'] || 'playing';

        const timeDisplay = document.getElementById('time-display');
        if (!timeDisplay) return;

        if (gameStatus === 'finished') {
            const restartSeconds = Math.ceil(restartTimer);
            timeDisplay.textContent = `Restart in: ${restartSeconds}s`;
            timeDisplay.style.color = '#ffaa00';
            timeDisplay.style.fontWeight = 'bold';

            // Обновляем уведомление о рестарте
            this.updateRestartTimer(restartSeconds);
        } else {
            // секунды в минуты и секунды
            const minutes = Math.floor(remainingTime / 60);
            const seconds = Math.floor(remainingTime % 60);
            const formattedTime = minutes.toString().padStart(2, '0') + ':' + seconds.toString().padStart(2, '0');

            timeDisplay.textContent = formattedTime;
            if (remainingTime <= 10) {
                timeDisplay.style.color = '#ff4444';
                timeDisplay.style.fontWeight = 'bold';
            } else if (remainingTime <= 30) {
                timeDisplay.style.color = '#ffaa00';
            } else {
                timeDisplay.style.color = '#ffffff';
                timeDisplay.style.fontWeight = 'normal';
            }
        }
    }

    showGameResults() {
        const winnerId = this.gameState['winner'];
        const players = Object.values(this.gameState.players);
        const restartTimer = this.gameState['restart-timer'] || 0;
        const restartSeconds = Math.ceil(restartTimer);

        let winnerInfo = 'No winner';
        if (winnerId) {
            const winner = players.find(p => p.id === winnerId);
            if (winner) {
                const bestTime = this.formatTime(winner['best-time'] || 0);
                winnerInfo = `🏆 Winner: ${winner.name} - ${bestTime}`;
            }
        }

        let resultsElement = document.getElementById('game-results');
        if (!resultsElement) {
            resultsElement = document.createElement('div');
            resultsElement.id = 'game-results';
            resultsElement.style.cssText = `
                position: fixed;
                top: 50%;
                left: 50%;
                transform: translate(-50%, -50%);
                background: rgba(0, 0, 0, 0.9);
                color: white;
                padding: 20px;
                border-radius: 10px;
                text-align: center;
                z-index: 1000;
                border: 2px solid gold;
                min-width: 300px;
            `;
            document.body.appendChild(resultsElement);
        }

        resultsElement.innerHTML = `
            <h2 style="color: gold; margin-bottom: 15px;">🏁 RACE FINISHED! 🏁</h2>
            <p style="font-size: 18px; margin-bottom: 10px;">${winnerInfo}</p>
            <p style="font-size: 16px; color: #ffaa00; margin-bottom: 15px;">
                Restarting in: <span id="restart-counter">${restartSeconds}</span> seconds
            </p>
            <p style="font-size: 14px; color: #ccc;">You cant move your car during countdown</p>
        `;

        resultsElement.style.display = 'block';
    }

    updateRestartTimer(seconds) {
        const counterElement = document.getElementById('restart-counter');
        if (counterElement) {
            counterElement.textContent = seconds;
        }
    }

    hideGameResults() {
        const resultsElement = document.getElementById('game-results');
        if (resultsElement) {
            resultsElement.style.display = 'none';
        }
    }

    // игровой цикл - вызывается постоянно для обновления отрисовки
    gameLoop() {
        // отрисовка текущего состояния
        this.render();
        // запрашиваем следующий кадр анимации (рекурсивный вызов)
        // requestAnimationFrame оптимизирует отрисовку под частоту обновления экрана
        requestAnimationFrame(() => this.gameLoop());
    }
}
// запуск игры после загрузки страницы
window.addEventListener('load', () => {

//    var pid = (Math.random(100)).toString();
//
//    console.log(pid);

    new RacingGameClient();
});