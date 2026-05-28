require("backend_stdlib")

--[[
  Тестовый плагин голосования.

  Команды:
    vote.cast    — проголосовать (payload: user_id, choice)
    vote.results — получить текущие результаты
    vote.reset   — сбросить голоса

  Варианты: "yes" | "no" | "abstain"

  Тест через curl (pluginId = 123e4567-e89b-12d3-a456-426614174001):

  # 1. Проголосовать
  curl -X POST http://localhost:8080/command \
    -H "Content-Type: application/json" \
    -H "X-User-Id: user1" \
    -d '{"name":"vote.cast","pluginId":"123e4567-e89b-12d3-a456-426614174001","chatId":"room1","args":{"user_id":"user1","choice":"yes"}}'

  # 2. Результаты
  curl -X POST http://localhost:8080/command \
    -H "Content-Type: application/json" \
    -H "X-User-Id: user1" \
    -d '{"name":"vote.results","pluginId":"123e4567-e89b-12d3-a456-426614174001","chatId":"room1","args":{}}'

  # 3. Сброс
  curl -X POST http://localhost:8080/command \
    -H "Content-Type: application/json" \
    -H "X-User-Id: user1" \
    -d '{"name":"vote.reset","pluginId":"123e4567-e89b-12d3-a456-426614174001","chatId":"room1","args":{}}'
]]

PLUGIN_ID = "test.vote"

-- Допустимые варианты голосования
local OPTIONS = { "yes", "no", "abstain" }

-- Стейт:
--   counts       — map<string, int>  : вариант → количество голосов
--   user_choices — map<string, string>: userId  → выбранный вариант
SetStateSchema({
    counts       = Types.map(Types.string, Types.int),
    user_choices = Types.map(Types.string, Types.string),
})

-- ─── Вспомогательная: собрать текущие счётчики в обычную таблицу ──────────────
local function collect_counts(state)
    local result = {}
    for _, opt in ipairs(OPTIONS) do
        result[opt] = state.counts:get(opt):get() or 0
    end
    return result
end

-- ─── vote.cast ────────────────────────────────────────────────────────────────
RegisterCommand({
    name = "vote.cast",

    in_schema = {
        user_id = Types.string,
        choice  = Types.string,
    },
    out_schema = {
        status = Types.string,
        counts = Types.map(Types.string, Types.int),
    },

    handler = function(ctx, payload)
        local uid    = payload.user_id
        local choice = payload.choice

        -- Проверяем корректность варианта
        local valid = false
        for _, opt in ipairs(OPTIONS) do
            if opt == choice then valid = true; break end
        end
        if not valid then
            return { status = "error:invalid_choice:" .. choice, counts = collect_counts(ctx.state) }
        end

        -- Убираем прежний голос пользователя (если он уже голосовал)
        local prev = ctx.state.user_choices:get(uid):get()
        if prev ~= nil and prev ~= "" then
            local c = ctx.state.counts:get(prev):get() or 0
            ctx.state.counts:get(prev):set(c > 0 and c - 1 or 0)
        end

        -- Засчитываем новый голос
        local c = ctx.state.counts:get(choice):get() or 0
        ctx.state.counts:get(choice):set(c + 1)

        -- Запоминаем выбор пользователя
        ctx.state.user_choices:get(uid):set(choice)

        return { status = "voted:" .. choice, counts = collect_counts(ctx.state) }
    end,
})

-- ─── vote.results ─────────────────────────────────────────────────────────────
RegisterCommand({
    name = "vote.results",

    in_schema  = {},
    out_schema = {
        counts = Types.map(Types.string, Types.int),
    },

    handler = function(ctx, payload)
        return { counts = collect_counts(ctx.state) }
    end,
})

-- ─── vote.reset ───────────────────────────────────────────────────────────────
RegisterCommand({
    name = "vote.reset",

    in_schema  = {},
    out_schema = {
        status = Types.string,
    },

    handler = function(ctx, payload)
        for _, opt in ipairs(OPTIONS) do
            ctx.state.counts:get(opt):set(0)
        end
        return { status = "reset_ok" }
    end,
})

