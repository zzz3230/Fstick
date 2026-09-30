require("backend_stdlib")

SetStateSchema({
    counter = Types.int,
})

RegisterCommand({
    name = "counter.increment",
    in_schema = { by = Types.int },
    out_schema = { value = Types.int },
    handler = function(ctx, payload)
        local v = ctx.state.counter:get() + payload.by
        ctx.state.counter:set(v)
        print("counter is now " .. v)
        return { value = v }
    end,
})
