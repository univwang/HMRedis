-- 1. 参数列表
-- 1.1 优惠券id
local voucherId = ARGV[1]
-- 1.2 用户id
local userId = ARGV[2]
-- 1.3 订单id
local orderId = ARGV[3]

-- 2. 数据key
-- 2.1 库存key
local stockKey = "seckill:stock:" .. voucherId
-- 2.2 订单key
local orderKey = "seckill:order:" .. voucherId

-- 3. 业务逻辑

-- 3.1 判断是否有库存
local stock = redis.call("get", stockKey)
if(tonumber(stock) <= 0) then
    return 1
end

-- 3.2 判断是否已经抢过
if(redis.call("sismember", orderKey, userId) == 1) then
    -- 已经抢过
    return 2
end

-- 3.3 扣减库存
redis.call("incrby", stockKey, -1)
-- 3.4 下单保存用户
redis.call("sadd", orderKey, userId)
-- 3.5 发送消息到队列中, XADD stream.orders * k1 v1 k2 v2
redis.call("xadd", "stream.orders", "*", "voucherId", voucherId, "userId", userId, "id", orderId)
return 0