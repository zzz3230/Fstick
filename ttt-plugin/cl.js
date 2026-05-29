// Команды
const joinGame  = backend.makeCommand('game.join');
const moveGame  = backend.makeCommand('game.move');
const resetGame = backend.makeCommand('game.reset');
const getUserId = backend.makeCommand('get_user_id');

// Текущий user_id (запрашиваем при старте)
let myUserId = null;
getUserId().then(res => { myUserId = res.user_id; });

// Вспомогательная функция для получения символа из ячейки
function getCellValue(row, col) {
    const key = `${row}_${col}`;
    return (state.board.value && state.board.value[key]) || ' ';
}

// Отрисовка игрового поля 3×3
function TicTacToeBoard() {
    const rows = [0, 1, 2];
    return Column({ gap: 4, self_align: 'center' },
        rows.map(row =>
            Row({ gap: 4, self_align: 'center' },
                rows.map(col => {
                    const symbol = getCellValue(row, col);
                    return Button(symbol, async () => {
                        await moveGame({ row, col });
                    }, {
                        width: 64,
                        height: 64,
                        font_size: 'title',
                        // простая блокировка – сервер всё равно проверит
                        disabled: !state.game_active.value || state.winner.value !== "",
                    });
                })
            )
        )
    );
}

// Отображение статуса игры
function GameStatus() {
    const winner = state.winner.value;
    const turn = state.turn.value;
    const active = state.game_active.value;
    const playerX = state.playerX.value;
    const playerO = state.playerO.value;

    let statusText = "";
    if (winner === "X") statusText = "🏆 Победили X!";
    else if (winner === "O") statusText = "🏆 Победили O!";
    else if (winner === "draw") statusText = "🤝 Ничья!";
    else if (active) statusText = `🎲 Ходят ${turn}`;
    else statusText = "⚡ Игра не активна (присоединитесь)";

    let playerInfo = "";
    if (playerX) playerInfo += `X: ${playerX}  `;
    if (playerO) playerInfo += `O: ${playerO}`;
    if (myUserId) {
        if (myUserId === playerX) playerInfo += `\n Вы играете за X`;
        else if (myUserId === playerO) playerInfo += `\n Вы играете за O`;
        else playerInfo += `\n Вы не в игре`;
    }

    return Column({ gap: 8 }, [
        Text(statusText, { content_align: 'center', font_size: 'subtitle' }),
        Text(playerInfo, { content_align: 'center', font_size: 'small' }),
    ]);
}

// Основной UI
DSL_CONTEXT.exports.__app = Column({ gap: 16, padding: 16 }, [

    Text('❌ Крестики‑нолики ⭕', {
        content_align: 'center',
        font_size: 'title',
    }),

    IfBlock(() => state.index.value, [
        TicTacToeBoard(),
        GameStatus(),
    ]),
    IfBlock(() => !state.index.value, [
        TicTacToeBoard(),
        GameStatus(),
    ]),


    Row({ gap: 12, self_align: 'center' }, [
        Button('➕ Присоединиться', async () => {
            await joinGame({});
        }, { weight: 1 }),

        Button('🔄 Сбросить игру', async () => {
            await resetGame({});
        }, { weight: 1 }),
    ]),
]);