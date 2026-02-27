require("backend_stdlib")

PLUGIN_ID = "test.plugin"

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

        if payload.clicked_times % 2 == 0 then
            return { status = "success" }
        end

        return {status = "failed"}

    end
})
