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

Types.user_scoped = function(fields)
    return {
        __type = "user_scoped",
        fields = fields
    }
end




----STATE API----

local function resolve(root, path)
    local ref = root
    for i = 1, #path do
        ref = ref[path[i]]
    end
    return ref
end

local function resolve_parent(root, path)
    local ref = root
    for i = 1, #path - 1 do
        ref = ref[path[i]]
    end
    return ref
end

local function ValueNode(root, path)
    return {
        get = function(self)
            return resolve(root, path)
        end,

        set = function(self, value)
            local parent = resolve_parent(root, path)
            parent[path[#path]] = value
        end
    }
end

local function ListNode(root, path, inner)
    return {
        set = function(self, index, value)
            local list = resolve(root, path)
            list[index] = value
        end,

        get = function(self, index)
            local newPath = { table.unpack(path) }
            table.insert(newPath, index)
            return wrapNode(root, newPath, inner)
        end
    }
end

local function MapNode(root, path, valueType)
    return {
        set = function(self, key, value)
            local map = resolve(root, path)
            map[key] = value
        end,

        get = function(self, key)
            local newPath = { table.unpack(path) }
            table.insert(newPath, key)
            return wrapNode(root, newPath, valueType)
        end
    }
end

local function ObjectNode(root, path, schema)
    local obj = {}

    for key, fieldType in pairs(schema) do
        local newPath = { table.unpack(path) }
        table.insert(newPath, key)

        obj[key] = wrapNode(root, newPath, fieldType)
    end

    return obj
end

function wrapNode(root, path, schema)
    local t = schema.__type

    if t == "int" or t == "string" or t == "float"
        or t == "datetime" or t == "user_id" then

        return ValueNode(root, path)

    elseif t == "list" then
        return ListNode(root, path, schema.inner)

    elseif t == "map" then
        return MapNode(root, path, schema.value)

    elseif t == "object" then
        return ObjectNode(root, path, schema.schema)

    elseif t == "user_scoped" then
        -- user_scoped acts as a map from userId → object(fields)
        return MapNode(root, path, Types.object(schema.fields))

    elseif t == "enum" then
        -- пока как value, но можно потом добавить проверку
        return ValueNode(root, path)
    end
end

local function createState(schema, data)
    return ObjectNode(data, {}, schema)
end

local function default_value(type)
    local t = type.__type

    if t == "int" then
        return 0
    elseif t == "float" then
        return 0.0
    elseif t == "string" then
        return ""
    elseif t == "datetime" then
        return 0
    elseif t == "user_id" then
        return 0
    elseif t == "enum" then
        -- первый вариант как дефолт
        return type.values and type.values[1] or nil
    end

    return nil
end

local function generate_minimal_data(schema, is_top)
    local t = schema.__type

    -- primitive
    if t == "int"
        or t == "float"
        or t == "string"
        or t == "datetime"
        or t == "user_id"
        or t == "enum" then
        return default_value(schema)
    end

    -- list
    if t == "list" then
        -- пустой массив по умолчанию
        return {}
    end

    -- map
    if t == "map" then
        return {}
    end

    -- user_scoped: empty map (userId → data)
    if t == "user_scoped" then
        return {}
    end

    -- object
    if t == "object" or is_top then
        local obj = {}
        -- top-level schema is a plain table {field=Type, ...}; wrapped Types.object uses schema.schema
        local fields = (t == "object") and schema.schema or schema
        for key, field_schema in pairs(fields) do
            obj[key] = generate_minimal_data(field_schema, false)
        end

        return obj
    end

    error("Unknown schema type: " .. tostring(t))
end

-- modifiers


StateSchema = {}
function SetStateSchema(schema)
    StateSchema = schema
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
    return type(v) == "string"
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
                    if v == allowed then
                        ok = true
                        break
                    end
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

    -- ===== user_scoped =====
    if t == "user_scoped" then
        if type(value) ~= "table" then return false, path .. " expected user_scoped table" end
        for userId, userVal in pairs(value) do
            if userVal ~= nil then
                local ok, err = validate(userVal, { __type = "object", schema = schema.fields }, path .. "[" .. tostring(userId) .. "]")
                if not ok then return false, err end
            end
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

CommandStatus = {
    SUCCESS = "SUCCESS",
    RUNTIME_ERROR = "RUNTIME_ERROR",
    COMMAND_NOT_FOUND = "COMMAND_NOT_FOUND",
    VALIDATION_ERROR = "VALIDATION_ERROR"
}

local function make_success(result)
    return { status = CommandStatus.SUCCESS, result = result }
end
local function make_error(error_code, error_message)
    if error_code == CommandStatus.SUCCESS then
        error("Bad error_code")
    end
    return { status = error_code, error = { message = error_message } }
end

local function execute_given_command_handler(command, payload)
    --print(PrettyTable(command.in_schema))
    local schema_ok, err_msg = ValidateTableSchema(payload, command.in_schema)
    if not schema_ok then
        return make_error(CommandStatus.VALIDATION_ERROR, "Invalid payload: " .. err_msg)
    end

    local state_data = _loadState()
    if state_data == nil then
        state_data = generate_minimal_data(StateSchema, true)
    end

    local wrapped_state = createState(StateSchema, state_data)

    local ok, result = pcall(command.handler, {state=wrapped_state}, payload)
    if not ok then
        return make_error(CommandStatus.RUNTIME_ERROR, "Error while handler: " .. result)
    end

    schema_ok, err_msg = ValidateTableSchema(state_data, StateSchema)
    if not schema_ok then
        return make_error(CommandStatus.VALIDATION_ERROR, "Invalid state value: " .. err_msg)
    end

    _saveState(state_data)

    schema_ok, err_msg = ValidateTableSchema(result, command.out_schema)

    if not schema_ok then
        return make_error(CommandStatus.VALIDATION_ERROR, "Invalid return value: " .. err_msg)
    end

    return make_success(result)
end

-- retruns {status=OK|COMMAND_NOT_FOUND|VALIDATION_ERROR|RUNTIME_ERROR, result={}|nil, error={}|nil}
function ExecuteCommandHandler(commandName, payload)
    -- Auto-sync: if plugin declares state_schema as a global, use it
    if state_schema ~= nil then
        StateSchema = state_schema
    end
    if Commands[commandName] == nil then
        return make_error(CommandStatus.COMMAND_NOT_FOUND, commandName .. " not found")
    end
    return execute_given_command_handler(Commands[commandName], payload)
end
