-- 库存回滚Lua脚本（用于订单取消/超时等场景）
-- KEYS[1]: 库存Key (flash:stock:{productId})
-- KEYS[2]: 用户购买记录Key (flash:bought:{productId})
-- ARGV[1]: 用户ID
-- ARGV[2]: 回滚数量

local stockKey = KEYS[1]
local boughtKey = KEYS[2]

-- 处理可能带引号的JSON字符串参数
local function parseNumber(val)
    if val == nil then return nil end
    local str = tostring(val):gsub('"', '')
    return tonumber(str)
end

local userId = tostring(ARGV[1]):gsub('"', '')
local quantity = parseNumber(ARGV[2]) or 0

-- 回滚库存
local stock = redis.call('GET', stockKey)
if stock then
    local currentStock = parseNumber(stock) or 0
    redis.call('SET', stockKey, tostring(currentStock + quantity))
end

-- 减少用户购买记录
local userBought = redis.call('HGET', boughtKey, userId)
if userBought then
    local boughtCount = parseNumber(userBought) or 0
    local newCount = boughtCount - quantity
    if newCount <= 0 then
        redis.call('HDEL', boughtKey, userId)
    else
        redis.call('HSET', boughtKey, userId, tostring(newCount))
    end
end

return 1

