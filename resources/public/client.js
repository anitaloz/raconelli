class RacingGameClient {
    constructor() {
        this.ws = null;
        this.gameState = { players: {}, gameTime: 0 };
        this.keys = {};
        this.canvas = document.getElementById('game-canvas');
        this.ctx = this.canvas.getContext('2d');

        this.setupEventListeners();
        this.connect();
        this.gameLoop();
    }

    setupEventListeners() {
        document.addEventListener('keydown', (e) => {
            this.keys[e.key.toLowerCase()] = true;
            this.sendInput();
        });

        document.addEventListener('keyup', (e) => {
            this.keys[e.key.toLowerCase()] = false;
            this.sendInput();
        });
    }

    connect() {
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        const wsUrl = `${protocol}//${window.location.host}/ws`;

        this.ws = new WebSocket(wsUrl);

        this.ws.onopen = () => {
            console.log('Connected to server');
            document.getElementById('connection-status').textContent = 'Connected';
        };

        this.ws.onclose = () => {
            console.log('Disconnected from server');
            document.getElementById('connection-status').textContent = 'Disconnected';
            setTimeout(() => this.connect(), 3000);
        };

        this.ws.onmessage = (event) => {
            try {
                const message = JSON.parse(event.data);
                if (message.type === 'game-state') {
                    this.gameState = message.state;
                    this.updateUI();
                }
            } catch (e) {
                console.error('Error parsing message:', e);
            }
        };

        this.ws.onerror = (error) => {
            console.error('WebSocket error:', error);
        };
    }

    sendInput() {
        if (this.ws && this.ws.readyState === WebSocket.OPEN) {
            const input = {
                up: this.keys['w'] || false,
                down: this.keys['s'] || false,
                left: this.keys['a'] || false,
                right: this.keys['d'] || false
            };

            this.ws.send(JSON.stringify({
                type: 'player-input',
                input: input
            }));
        }
    }

    drawCar(ctx, player) {
        ctx.save();
        ctx.translate(player.x, player.y);
        ctx.rotate(player.angle * Math.PI / 180);

        // Car body
        ctx.fillStyle = player.color;
        ctx.fillRect(-15, -8, 30, 16);

        // Car details
        ctx.fillStyle = '#333';
        ctx.fillRect(5, -6, 8, 12);
        ctx.fillRect(-13, -6, 8, 12);

        // Wheels
        ctx.fillStyle = '#222';
        ctx.fillRect(-12, -10, 5, 4);
        ctx.fillRect(-12, 6, 5, 4);
        ctx.fillRect(7, -10, 5, 4);
        ctx.fillRect(7, 6, 5, 4);

        ctx.restore();
    }

    drawTrack(ctx) {
        // Draw track boundaries
        ctx.strokeStyle = '#8B4513';
        ctx.lineWidth = 40;
        ctx.strokeRect(0, 0, 800, 600);

        // Draw road
        ctx.fillStyle = '#7f8c8d';
        ctx.fillRect(20, 20, 760, 560);

        // Draw center line
        ctx.strokeStyle = '#ffffff';
        ctx.lineWidth = 2;
        ctx.setLineDash([10, 10]);
        ctx.strokeRect(40, 40, 720, 520);
        ctx.setLineDash([]);
    }

    render() {
        // Clear canvas
        this.ctx.fillStyle = '#27ae60';
        this.ctx.fillRect(0, 0, this.canvas.width, this.canvas.height);

        // Draw track
        this.drawTrack(this.ctx);

        // Draw players
        Object.values(this.gameState.players).forEach(player => {
            this.drawCar(this.ctx, player);
        });
    }

    updateUI() {
        const players = Object.values(this.gameState.players);
        document.getElementById('player-count').textContent = players.length;

        const playersList = document.getElementById('players-list');
        playersList.innerHTML = '';

        players.forEach(player => {
            const playerElement = document.createElement('div');
            playerElement.className = 'player-item';
            playerElement.innerHTML = `
                        <div class="player-color" style="background-color: ${player.color}"></div>
                        Player ${player.id.substring(0, 8)}
                    `;
            playersList.appendChild(playerElement);
        });
    }

    gameLoop() {
        this.render();
        requestAnimationFrame(() => this.gameLoop());
    }
}

// Start the game when page loads
window.addEventListener('load', () => {
    new RacingGameClient();
});