% bot.pl
:- use_module(library(http/websocket)).

start_bot :-
    URL = 'ws://localhost:8080/ws',
    http_open_websocket(URL, WS, []),
    ws_send(WS, text('{"type": "player-id", "playerId": "1"}')).

%  swipl -s 'path'
%  start_bot.