require("backend_stdlib")

PLUGIN_ID = "test.plugin"

SetStateSchema({
    x = Types.int
})

RegisterCommand({
    name = "user.clicked",

    in_schema = {
        clicked_times = Types.int,
    },
    out_schema = {
        status = Types.string
    },

    handler = function(ctx, payload)

        --ctx:emit(userVoteEvent, {clicked_times = payload.clicked_times})

        local val = ctx.state.x:get()
        ctx.state.x:set(val + payload.clicked_times)

        if payload.clicked_times % 2 == 0 then
            return { status = "ok,counter=" .. val }
        end

        return {status = "failed,counter=" .. val}

    end
})
