require("backend_stdlib")

--[[
  Плагин голосования — серверная часть (runtime: sv.lua@^0)

  Стейт:
    counts       map<string, int>    вариант → кол-во голосов
    user_choices map<string, string> userId  → выбранный вариант

  Команды:
    vote.cast   { choice }  — проголосовать / сменить голос
    vote.reset  {}          — сбросить голоса (только для теста)

  user_id поступает автоматически через заголовок X-User-Id
  и доступен в Lua-глобале _user_id (инжектируется рантаймом).
]]

PLUGIN_ID = "fstick.vote"

local OPTIONS = { "yes", "no", "abstain" }

-- Стейт:
--   counts       map<string, int>             вариант → кол-во голосов
--   user_choices user_scoped { choice: string } per-user выбор
SetStateSchema({
    counts       = Types.map(Types.string, Types.int),
    user_choices = Types.user_scoped({ choice = Types.string }),
})

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
        choice = Types.string,
    },
    out_schema = {
        status = Types.string,
        counts = Types.map(Types.string, Types.int),
    },

    handler = function(ctx, payload)
        local uid    = _user_id
        local choice = payload.choice

        -- Проверяем корректность варианта
        local valid = false
        for _, opt in ipairs(OPTIONS) do
            if opt == choice then valid = true; break end
        end
        if not valid then
            return { status = "error:invalid_choice", counts = collect_counts(ctx.state) }
        end

        -- Читаем предыдущий голос безопасно (entry может отсутствовать)
        local prev = nil
        local ok, val = pcall(function()
            return ctx.state.user_choices:get(uid).choice:get()
        end)
        if ok and val ~= nil and val ~= "" then
            prev = val
        end

        -- Убираем предыдущий голос
        if prev ~= nil then
            local c = ctx.state.counts:get(prev):get() or 0
            ctx.state.counts:get(prev):set(c > 0 and c - 1 or 0)
        end

        -- Добавляем новый голос
        local c = ctx.state.counts:get(choice):get() or 0
        ctx.state.counts:get(choice):set(c + 1)

        -- Сохраняем выбор через MapNode.set (инициализирует запись если её нет)
        ctx.state.user_choices:set(uid, { choice = choice })

        return { status = "ok", counts = collect_counts(ctx.state) }
    end,
})

-- ─── vote.reset ───────────────────────────────────────────────────────────────

RegisterCommand({
    name = "vote.reset",

    in_schema  = {},
    out_schema = { status = Types.string },

    handler = function(ctx, payload)
        for _, opt in ipairs(OPTIONS) do
            ctx.state.counts:get(opt):set(0)
        end
        return { status = "ok" }
    end,
})

