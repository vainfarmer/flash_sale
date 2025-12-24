-- 秒杀库存扣减Lua脚本
-- 原子性操作：检查库存 + 检查重复购买 + 扣减库存 + 记录购买

-- KEYS[1]: 库存Key (flash:stock:{productId})
-- KEYS[2]: 用户购买记录Key (flash:bought:{productId})
-- ARGV[1]: 用户ID
-- ARGV[2]: 扣减数量
-- ARGV[3]: 限购数量

-- 返回值:
-- -1: 库存不足
-- -2: 重复购买/超出限购
-- >=0: 扣减成功，返回剩余库存

local stockKey = KEYS[1]
local boughtKey = KEYS[2]
local userId = ARGV[1]
local quantity = tonumber(ARGV[2])
local limitPerUser = tonumber(ARGV[3])

-- 检查用户是否已购买（防止重复下单）
local userBought = redis.call('HGET', boughtKey, userId)
if userBought then
    local boughtCount = tonumber(userBought)
    if boughtCount + quantity > limitPerUser then
        return -2  -- 超出限购数量
    end
end

-- 检查库存
local stock = redis.call('GET', stockKey)
if not stock then
    return -1  -- 库存Key不存在
end

local currentStock = tonumber(stock)
if currentStock < quantity then
    return -1  -- 库存不足
end

-- 扣减库存
local newStock = currentStock - quantity
redis.call('SET', stockKey, newStock)

-- 记录用户购买数量
if userBought then
    redis.call('HINCRBY', boughtKey, userId, quantity)
else
    redis.call('HSET', boughtKey, userId, quantity)
end

return newStock

