% Подключаем нужные библиотеки для работы с WebSocket, HTTP и JSON
:- use_module(library(http/websocket)).
:- use_module(library(http/http_open)).
:- use_module(library(http/json)).

% =========================================================
% ТРАЕКТОРИЯ — список точек, по которым должен ехать бот
% =========================================================

trajectory([
    point(685, 120),
    point(685, 236),
    point(463,236),
    point(463, 360),
    point(685, 365),
    point(695, 495),
    point(355, 485),
    point(355, 330),
    point(280, 234),
    point(220, 330),
    point(220, 420),
    point(150, 487),
    point(83, 415),
    point(110, 286),
    point(179, 118),
    point(420, 100)
]).

% =========================================================
% МАТЕМАТИКА — вспомогательные функции
% =========================================================

% Нормализация угла в диапазон [-180, 180]
normalize_angle(Angle, Norm) :-
    Tmp is Angle - 360 * floor(Angle / 360),
    ( Tmp > 180 -> Norm is Tmp - 360
    ;               Norm is Tmp
    ).

% Вычисление расстояния между двумя точками
distance(X1, Y1, X2, Y2, D) :-
    DX is X2 - X1,
    DY is Y2 - Y1,
    D is sqrt(DX*DX + DY*DY).

% Проверка, что текущая позиция близка к целевой точке
close_enough(CX, CY, point(TX,TY)) :-
    distance(CX, CY, TX, TY, D),
    D < 4.

% =========================================================
% ОТПРАВКА ВВОДА — формируем JSON и отправляем на сервер
% =========================================================

send_input(WS, PlayerId, Input) :-
    % Извлекаем значения из словаря Input
    get_dict(up,    Input, Up0),    (Up0    -> Up=true ; Up=false),
    get_dict(down,  Input, Down0),  (Down0  -> Down=true ; Down=false),
    get_dict(left,  Input, Left0),  (Left0  -> Left=true ; Left=false),
    get_dict(right, Input, Right0), (Right0 -> Right=true ; Right=false),

    % Формируем словарь с фиксированными булевыми значениями
    Fixed = _{up:Up, down:Down, left:Left, right:Right},

    % Преобразуем в JSON-строку
    atom_json_dict(Json,
        _{ type:"player-input",
           playerId:PlayerId,
           input:Fixed
         },
        [as(string)]),

    % Логируем и отправляем сообщение
    format("JSON sent: ~s~n", [Json]),
    ws_send(WS, text(Json)),
    format("Sent input for player ~w: ~w~n", [PlayerId, Fixed]).

% =========================================================
% УПРАВЛЕНИЕ — логика принятия решения
% =========================================================

decide_input(CX, CY, CAngle, point(TX,TY), Input) :-
    % Вычисляем вектор до цели
    DX is TX - CX,
    DY is TY - CY,

    % Угол до цели в градусах
    AngleToTarget is atan2(DY, DX) * 180 / pi,

    % Разница между текущим углом и направлением к цели
    Delta is AngleToTarget - CAngle,
    normalize_angle(Delta, D),

    % Логируем разницу углов
    format("D: ~w~n", [D]),

    % Если цель левее — жмём влево
    ( D < -9 -> Left  = true ; Left  = false ),
    % Если цель правее — жмём вправо
    ( D > 9 -> Right = true ; Right = false ),

    % Если угол почти совпадает — жмём газ
    AbsD is abs(D),
    ( AbsD < 30 -> Up = true ; Up = false ),

    % Формируем словарь ввода
    Input = _{
        up:Up,
        down:false,
        left:Left,
        right:Right
    },
    format("Decided input: ~w~n", [Input]).

% =========================================================
% ПАРСИНГ СОСТОЯНИЯ — читаем сообщения от сервера
% =========================================================

listen_state(WS, PlayerId, State) :-
    % Получаем сообщение из WebSocket
    ws_receive(WS, Msg, [timeout(1.0)]),
    (   Msg.opcode == text,
        % Пробуем распарсить JSON
        catch(atom_json_dict(Msg.data, Dict, []), _, fail),
        ( Dict.type == "game-state" ->
            % Извлекаем состояние игрока
            Players = Dict.state.players,
            atom_string(KeyAtom, PlayerId),
            (   get_dict(KeyAtom, Players, P)
            ->  State = _{x:P.x, y:P.y, angle:P.angle}
            ;   State = none )
        ; Dict.type == "join-success" ->
            State = join_success
        ; Dict.type == "join-error" ->
            State = join_error
        ; State = none
        )
    ;   State = none).

% =========================================================
% ОДИН ШАГ БОТА — проверка точки и движение
% =========================================================

step_bot(_, _, [], _, []) :- !.  % если траектория пуста — конец

step_bot(WS, PlayerId, [Point|Rest], State, NewTrajectory) :-
    ( close_enough(State.x, State.y, Point)
    -> format("Reached point ~w, moving to next.~n", [Point]),
       NewTrajectory = Rest
    ;  decide_input(State.x, State.y, State.angle, Point, Input),
       send_input(WS, PlayerId, Input),
       NewTrajectory = [Point|Rest]
    ).

% =========================================================
% ГЛАВНЫЙ ЦИКЛ — непрерывное выполнение шагов
% =========================================================

loop(WS, PlayerId, Trajectory) :-
    listen_state(WS, PlayerId, State),
    (   State == join_success
    ->  format("Join success~n"),
        sleep(0.012),
        loop(WS, PlayerId, Trajectory)

    ;   State == join_error
    ->  format("Join error, stopping.~n")

    ;   State == none
    ->  sleep(0.012),
        loop(WS, PlayerId, Trajectory)

    ;   step_bot(WS, PlayerId, Trajectory, State, NextTrajectory),
        sleep(0.012),  % всегда пауза!
        loop(WS, PlayerId, NextTrajectory)
    ).


% =========================================================
% ЗАПУСК — подключение и старт бота
% =========================================================

start_bot :-
    % Подключаемся к серверу WebSocket
    http_open_websocket('ws://localhost:8080/ws', WS, []),

    % Регистрируем игрока с id "1"
    ws_send(WS, text("{\"type\":\"player-id\",\"playerId\":\"1\"}")),

    format("Bot started, waiting for server...~n"),

    % Загружаем траекторию
    trajectory(Trajectory),

    % Запускаем главный цикл
    loop(WS, "1", Trajectory).
