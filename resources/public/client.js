// ГЛАВНЫЙ КЛАСС КЛИЕНТА
class RacingGameClient {

    constructor() {
        this.ws = null;    // вебсокет соединение
        this.gameState = { players: {}, gameTime: 0 };  // состояние игры
        this.keys = {};    // состояние нажатых клавиш
        this.canvas = document.getElementById('game-canvas');      // html канвас элемент
        this.ctx = this.canvas.getContext('2d');                   // 2д контекст для рисования на канвас

        // вызов методов инициализации (все функции ниже)
        this.setupEventListeners();  // настройка обработчиков событий
        this.connect();              // подключение к серверу
        this.gameLoop();             // запуск игрового цикла
    }

    // настройка обработчиков событий клавиатуры
    setupEventListeners() {
        // обработчик нажатия клавиш
        document.addEventListener('keydown', (e) => {
            this.keys[e.key.toLowerCase()] = true;   // true = нажата (состояние клавиши)

            // отправляем текущий ввод на сервер
            this.sendInput();
        });

        // обработчик отпускания клавиш
        document.addEventListener('keyup', (e) => {
            this.keys[e.key.toLowerCase()] = false;   // false = нажата (состояние клавиши)

            // отправляем текущий ввод на сервер
            this.sendInput();
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

            // обновляем статус подключения в UI
            document.getElementById('connection-status').textContent = 'Connected';
        };

        // обработчик закрытия соединения
        this.ws.onclose = () => {
            console.log('Disconnected from server');

            // обновляем статус подключения в UI
            document.getElementById('connection-status').textContent = 'Disconnected';

            // попытка переподключения через 3 секунды
            setTimeout(() => this.connect(), 3000);
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
            // формируем объект ввода на основе нажатых клавиш
            const input = {
                up: this.keys['w'] || false,
                down: this.keys['s'] || false,
                left: this.keys['a'] || false,
                right: this.keys['d'] || false
            };

            // отправляем сообщение на сервер в формате JSON
            this.ws.send(JSON.stringify({
                type: 'player-input',   // тип сообщения
                input: input
            }));
            // обработка в вебсокет хэндлере в r.web_socket
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
        ctx.fillStyle = player.color;
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

    // отрисовка гоночного трека
    drawTrack(ctx) {

       // границы трека
        ctx.strokeStyle = '#8B4513';
        ctx.lineWidth = 40;
        ctx.strokeRect(0, 0, 800, 600);

        // трек
        ctx.fillStyle = '#7f8c8d';
        ctx.fillRect(20, 20, 760, 560);

        // центральная разделительная линия
        ctx.strokeStyle = '#ffffff';
        ctx.lineWidth = 2;
        ctx.setLineDash([10, 10]);
        ctx.strokeRect(40, 40, 720, 520);
        ctx.setLineDash([]);
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
            this.drawCar(this.ctx, player);
        });
    }

    // обновление пользовательского интерфейса (боковая панель)
    updateUI() {
        // получаем массив всех игроков
        const players = Object.values(this.gameState.players);
        document.getElementById('player-count').textContent = players.length;

        // обновляем счетчик игроков
        const playersList = document.getElementById('players-list');
        playersList.innerHTML = '';

        // для каждого игрока создаем элемент в списке
        players.forEach(player => {
            const playerElement = document.createElement('div');
            playerElement.className = 'player-item';

            // HTML содержимое элемента игрока:
            // - Цветной квадратик с цветом машины
            // - Укороченный ID игрока (первые 8 символов)
            playerElement.innerHTML = `
                        <div class="player-color" style="background-color: ${player.color}"></div>
                        Player ${player.id.substring(0, 8)}
                    `;

            // добавляем элемент в список
            playersList.appendChild(playerElement);
        });
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
    new RacingGameClient();
});