Types = {}

local function base_type(name)
    return {
        __type = name
    }
end

Types.int = base_type("int")
Types.string = base_type("string")
Types.float = base_type("float")
Types.datetime = base_type("datetime")
Types.user_id = base_type("user_id")

Types.enum = function(values)
    return {
        __type = "enum",
        values = values,
        flags = function(self)
            self.__flags = true
            return self
        end
    }
end

Types.list = function(inner)
    return {
        __type = "list",
        inner = inner
    }
end

Types.map = function(key, value)
    return {
        __type = "map",
        key = key,
        value = value
    }
end

Types.object = function(schema)
    return {
        __type = "object",
        schema = schema
    }
end


-- modifiers


function RegisterReduce(args)

end

function RegisterEvent(args)
    return {}
end

Commands = {}
function RegisterCommand(args)
    Commands[args.name] = args
end


function PrettyTable(t, indent, visited)
    indent = indent or 0
    visited = visited or {}

    if type(t) ~= "table" then
        return tostring(t)
    end

    if visited[t] then
        return "<cycle>"
    end

    visited[t] = true

    local lines = {}
    local prefix = string.rep("  ", indent)
    table.insert(lines, "{")

    for k, v in pairs(t) do
        local keyStr

        if type(k) == "string" then
            keyStr = k
        else
            keyStr = "[" .. tostring(k) .. "]"
        end

        local valueStr
        if type(v) == "table" then
            valueStr = PrettyTable(v, indent + 1, visited)
        elseif type(v) == "string" then
            valueStr = '"' .. v .. '"'
        else
            valueStr = tostring(v)
        end

        table.insert(
            lines,
            string.rep("  ", indent + 1) ..
            keyStr .. " = " .. valueStr .. ","
        )
    end

    table.insert(lines, prefix .. "}")
    return table.concat(lines, "\n")
end





local function is_int(v)
    return type(v) == "number" and math.floor(v) == v
end

local function is_float(v)
    return type(v) == "number"
end

local function is_string(v)
    return type(v) == "string"
end

local function is_datetime(v)
    -- упрощённая проверка ISO-строки
    return type(v) == "string"
end

local function is_user_id(v)
    return type(v) == "string" or is_int(v)
end


local function validate(value, schema, path)
    path = path or "root"

    -- Если schema обычная таблица без __type → считаем object
    if type(schema) == "table" and not schema.__type then
        if type(value) ~= "table" then
            return false, path .. " expected object"
        end

        for field, fieldSchema in pairs(schema) do
            local ok, err = validate(value[field], fieldSchema, path .. "." .. field)
            if not ok then
                return false, err
            end
        end

        return true
    end

    if not schema or not schema.__type then
        return false, path .. ": invalid schema"
    end

    local t = schema.__type

    -- ===== base types =====
    if t == "int" then
        if not is_int(value) then return false, path .. " expected int" end
        return true
    end
    if t == "float" then
        if not is_float(value) then return false, path .. " expected float" end
        return true
    end
    if t == "string" then
        if not is_string(value) then return false, path .. " expected string" end
        return true
    end
    if t == "datetime" then
        if not is_datetime(value) then return false, path .. " expected datetime string" end
        return true
    end
    if t == "user_id" then
        if not is_user_id(value) then return false, path .. " expected user_id" end
        return true
    end

    -- ===== enum =====
    if t == "enum" then
        if schema.__flags then
            if type(value) ~= "table" then
                return false, path .. " expected flags table"
            end
            for _, v in pairs(value) do
                local ok = false
                for _, allowed in ipairs(schema.values) do
                    if v == allowed then ok = true break end
                end
                if not ok then
                    return false, path .. " invalid enum flag: " .. tostring(v)
                end
            end
            return true
        else
            for _, allowed in ipairs(schema.values) do
                if value == allowed then return true end
            end
            return false, path .. " invalid enum value"
        end
    end

    -- ===== list =====
    if t == "list" then
        if type(value) ~= "table" then return false, path .. " expected list" end
        for i, v in ipairs(value) do
            local ok, err = validate(v, schema.inner, path .. "[" .. i .. "]")
            if not ok then return false, err end
        end
        return true
    end

    -- ===== map =====
    if t == "map" then
        if type(value) ~= "table" then return false, path .. " expected map" end
        for k, v in pairs(value) do
            local okKey, errKey = validate(k, schema.key, path .. ".<key>")
            if not okKey then return false, errKey end
            local okVal, errVal = validate(v, schema.value, path .. "[" .. tostring(k) .. "]")
            if not okVal then return false, errVal end
        end
        return true
    end

    -- ===== object =====
    if t == "object" then
        if type(value) ~= "table" then return false, path .. " expected object" end
        for field, fieldSchema in pairs(schema.schema) do
            local ok, err = validate(value[field], fieldSchema, path .. "." .. field)
            if not ok then return false, err end
        end
        return true
    end

    return false, path .. ": unknown type " .. tostring(t)
end

function ValidateTableSchema(value, schema)
    return validate(value, schema)
end




local function execute_given_command_handler(command, payload)
    print(PrettyTable(command.in_schema))
    local schema_ok, err_msg = ValidateTableSchema(payload, command.in_schema)
    if not schema_ok then
        return "Invalid payload: " .. err_msg
    end

    local ok, result = pcall(command.handler, {}, payload)

    if not ok then
        return "Error while handler: " .. result
    end

    schema_ok, err_msg = ValidateTableSchema(result, command.out_schema)

    if not schema_ok then
        return "Invalid return value: " .. result
    end

    return "OK: " .. PrettyTable(result)
end


function ExecuteCommandHandler(commandName, payload)
    if Commands[commandName] == nil then
        return "Command " .. commandName .. " not found"
    end
    return execute_given_command_handler(Commands[commandName], payload)
end