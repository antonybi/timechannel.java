redis.replicate_commands()

local timeValue = redis.call('TIME')
local serverTime = tonumber(timeValue[1]) * 1000 + math.floor(tonumber(timeValue[2]) / 1000)

-- 获取时间最小的层，则为当前应选取的层
local channel = ARGV[2]

-- 分配申请的授权期
local ttl = tonumber(ARGV[3])
local expiryTime = serverTime + ttl
-- 更新服务器数据
redis.call('zadd', 'space:' .. ARGV[1] .. ':expiryTime:channel', expiryTime, channel)

-- 成功分配授权
return { 1, expiryTime }