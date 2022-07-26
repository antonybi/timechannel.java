-- lua5.3 语法，Redis暂未支持此版本
--local timeInMs = tonumber(timeValue[1]) * 1000 + tonumber(timeValue[2]) // 1000
--local blockStartTime = timeInMs & 0xFFFFFFFFFFFFFFF0

local timeValue = redis.call('TIME')
local timeInMs = tonumber(timeValue[1]) * 1000 + math.floor(tonumber(timeValue[2]) / 1000)
-- 每16ms为一个时间片
local blockStartTime = timeInMs / 16 * 16

-- 获取时间最小的层，则为当前应选取的层
local blockValue = redis.call('zrange', 'tw:endTime:channel', 0, 0, 'WITHSCORES')
local channel = 0
redis.replicate_commands()
if not blockValue[1] then
    -- 初始化授权期的有序队列
    for i = 0, 16384 - 1 do
        redis.call('zadd', 'tw:endTime:channel', 0, i)
    end
else
    channel = tonumber(blockValue[1])
    local lastBlockEndTime = tonumber(blockValue[2])
    if lastBlockEndTime > blockStartTime then
        -- 还在保护期范围，申请失败
        return { 0, channel, blockStartTime, lastBlockEndTime }
    end
end

-- 分配申请的授权期，为防止申请过于频繁或占用时间过长，范围在1s到30m之间
local authDuration = tonumber(ARGV[1])
if authDuration < 1000 then
    authDuration = 1000
elseif authDuration > 1800000 then
    authDuration = 1800000
end
local blockEndTime = blockStartTime + authDuration
-- 更新服务器数据
redis.call('zadd', 'tw:endTime:channel', blockEndTime, channel)
-- 记录一条日志信息用于事后分析
redis.call('set', 'tw:channel:' .. channel .. ':log', ARGV[2])

-- 成功分配授权
return { 1, channel, blockStartTime, blockEndTime }