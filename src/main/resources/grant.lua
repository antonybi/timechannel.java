redis.replicate_commands()

local timeValue = redis.call('TIME')
local serverTime = tonumber(timeValue[1]) * 1000 + math.floor(tonumber(timeValue[2]) / 1000)

local space = ARGV[1]
local channelQuantity = tonumber(ARGV[2])
local ttl = tonumber(ARGV[3])
local appName = ARGV[4]

-- 采用LRU策略，选取过期时间最早的频道
local leaseValue = redis.call('zrange', 'space:' .. space .. ':expiryTime:channel', 0, 0, 'WITHSCORES')
local channel = 0

if not leaseValue[1] then
    -- 初始化授权期的有序队列
    for i = 0, channelQuantity do
        redis.call('zadd', 'space:' .. space .. ':expiryTime:channel', 0, i)
    end
else
    channel = tonumber(leaseValue[1])
    local lastExpiryTime = tonumber(leaseValue[2])
    if lastExpiryTime > serverTime then
        -- 还在保护期范围，申请失败
        return { 0, channel, serverTime, lastExpiryTime }
    end
end

-- 分配申请的授权期
local expiryTime = serverTime + ttl
-- 更新服务器数据
redis.call('zadd', 'space:' .. space .. ':expiryTime:channel', expiryTime, channel)
-- 记录一条日志信息用于事后分析
redis.call('set', 'space:' .. space .. ':channel:' .. channel .. ':log', appName)

-- 成功分配授权
return { 1, channel, serverTime, expiryTime }