---@diagnostic disable: undefined-global
require("backend_stdlib")

PLUGIN_ID = "example.tictactoe"

SetStateSchema({
    board        = Types.map(Types.string, Types.string),
    turn         = Types.string,
    winner       = Types.string,
    playerX      = Types.string,
    playerO      = Types.string,
    game_active  = Types.bool,
    index = Types.bool
})

-- ──────────────── команда присоединения ──────────────────────────────────
RegisterCommand({
    name = "game.join",
    in_schema = {},
    out_schema = { status = Types.string },

    handler = function(ctx, payload)
        ctx.state.index:set(!ctx.state.index:get())
        local uid = _user_id
        local pX = ctx.state.playerX:get()
        local pO = ctx.state.playerO:get()

        if uid == pX then return { status = "already_joined_as_X" } end
        if uid == pO then return { status = "already_joined_as_O" } end

        if pX == "" then
            ctx.state.playerX:set(uid)
            ctx.send_message("Игрок " .. uid .. " присоединился за X")
        elseif pO == "" then
            ctx.state.playerO:set(uid)
            ctx.send_message("Игрок " .. uid .. " присоединился за O")
            if ctx.state.playerX:get() ~= "" and ctx.state.playerO:get() ~= "" then
                -- инициализация поля без отдельной функции
                for row = 0, 2 do
                    for col = 0, 2 do
                        ctx.state.board:get(row .. "_" .. col):set("")
                    end
                end
                ctx.state.game_active:set(true)
                ctx.state.turn:set("X")
                ctx.state.winner:set("")
                ctx.send_message("Игра началась! Первым ходит X — игрок " .. ctx.state.playerX:get())
            end
        else
            return { status = "game_full" }
        end
        return { status = "ok" }
    end
})

-- ──────────────── команда хода ───────────────────────────────────────────
RegisterCommand({
    name = "game.move",
    in_schema = { row = Types.int, col = Types.int },
    out_schema = { status = Types.string },

    handler = function(ctx, payload)
        ctx.state.index:set(!ctx.state.index:get())
        local uid = _user_id
        local row = payload.row
        local col = payload.col

        if not ctx.state.game_active:get() then return { status = "game_not_active" } end
        if ctx.state.winner:get() ~= "" then return { status = "game_already_finished" } end

        local turn = ctx.state.turn:get()
        local expected = (turn == "X") and ctx.state.playerX:get() or ctx.state.playerO:get()
        if uid ~= expected then return { status = "not_your_turn" } end

        local key = row .. "_" .. col
        if ctx.state.board:get(key):get() ~= "" then return { status = "cell_taken" } end

        -- ставим знак
        ctx.state.board:get(key):set(turn)
        ctx.send_message("Игрок " .. uid .. " поставил " .. turn .. " на (" .. row .. "," .. col .. ")")

        -- ----- проверка победителя без отдельной функции -----
        local winner = nil
        -- выигрышные линии
        local lines = {
            { "0_0", "0_1", "0_2" }, { "1_0", "1_1", "1_2" }, { "2_0", "2_1", "2_2" },
            { "0_0", "1_0", "2_0" }, { "0_1", "1_1", "2_1" }, { "0_2", "1_2", "2_2" },
            { "0_0", "1_1", "2_2" }, { "0_2", "1_1", "2_0" }
        }
        for _, line in ipairs(lines) do
            local a = ctx.state.board:get(line[1]):get()
            local b = ctx.state.board:get(line[2]):get()
            local c = ctx.state.board:get(line[3]):get()
            if a ~= "" and a == b and b == c then
                winner = a
                break
            end
        end
        if not winner then
            -- проверка на ничью
            local full = true
            for r = 0, 2 do
                for c = 0, 2 do
                    if ctx.state.board:get(r .. "_" .. c):get() == "" then
                        full = false
                        break
                    end
                end
                if not full then break end
            end
            if full then winner = "draw" end
        end
        -- -----------------------------------------------------

        if winner then
            ctx.state.game_active:set(false)
            if winner == "draw" then
                ctx.state.winner:set("draw")
                ctx.send_message("Ничья! Игра закончена.")
            else
                ctx.state.winner:set(winner)
                local winner_uid = (winner == "X") and ctx.state.playerX:get() or ctx.state.playerO:get()
                ctx.send_message("Игрок " .. winner_uid .. " (" .. winner .. ") победил! Игра закончена.")
            end
            return { status = "game_finished" }
        end

        -- переключаем ход
        local new_turn = (turn == "X") and "O" or "X"
        ctx.state.turn:set(new_turn)
        local next_uid = (new_turn == "X") and ctx.state.playerX:get() or ctx.state.playerO:get()
        ctx.send_message("Теперь ходит " .. new_turn .. " — игрок " .. next_uid)

        return { status = "ok" }
    end
})

-- ──────────────── команда сброса ─────────────────────────────────────────
RegisterCommand({
    name = "game.reset",
    in_schema = {},
    out_schema = { status = Types.string },

    handler = function(ctx, payload)
        ctx.state.index:set(!ctx.state.index:get())
        for row = 0, 2 do
            for col = 0, 2 do
                ctx.state.board:get(row .. "_" .. col):set("")
            end
        end
        ctx.state.turn:set("")
        ctx.state.winner:set("")
        ctx.state.playerX:set("")
        ctx.state.playerO:set("")
        ctx.state.game_active:set(false)
        ctx.send_message("Игра сброшена. Используйте /join, чтобы присоединиться.")
        return { status = "ok" }
    end
})

-- ──────────────── вспомогательная команда (узнать свой user_id) ───────────
RegisterCommand({
    name = "get_user_id",
    in_schema = {},
    out_schema = { user_id = Types.string },
    handler = function(ctx, payload)
        ctx.state.index:set(!ctx.state.index:get())
        return { user_id = _user_id }
    end
})