-- 库存回滚Lua脚本（用于订单取消/超时等场景）
-- KEYS[1]: 库存Key (flash:stock:{productId})
-- KEYS[2]: 用户购买记录Key (flash:bought:{productId})
-- ARGV[1]: 用户ID
-- ARGV[2]: 回滚数量

local stockKey = KEYS[1]
local boughtKey = KEYS[2]
local userId = ARGV[1]
local quantity = tonumber(ARGV[2])

-- 回滚库存
local stock = redis.call('GET', stockKey)
if stock then
    local currentStock = tonumber(stock)
    redis.call('SET', stockKey, currentStock + quantity)
end

-- 减少用户购买记录
local userBought = redis.call('HGET', boughtKey, userId)
if userBought then
    local boughtCount = tonumber(userBought)
    local newCount = boughtCount - quantity
    if newCount <= 0 then
        redis.call('HDEL', boughtKey, userId)
    else
        redis.call('HSET', boughtKey, userId, newCount)
    end
end

return 1

