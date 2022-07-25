-- lua5.3 语法，Redis暂未支持此版本
--local timeInMs = tonumber(timeValue[1]) * 1000 + tonumber(timeValue[2]) // 1000
--local brickStartTime = timeInMs & 0xFFFFFFFFFFFFFFF0

local timeValue = redis.call('TIME')
local timeInMs = tonumber(timeValue[1]) * 1000 + math.floor(tonumber(timeValue[2]) / 1000)
-- 每16ms为一个时间片
local brickStartTime = timeInMs / 16 * 16

-- 获取当前层数
local brickValue = redis.call('zrange', 'tb:endTime:brickCourse', 0, 0, 'WITHSCORES')
local brickCourse = 0
redis.replicate_commands()
if not brickValue[1] then
    -- 初始化授权期的有序队列
    for i = 0, 16384 - 1 do
        redis.call('zadd', 'tb:endTime:brickCourse', 0, i)
    end
else
    brickCourse = tonumber(brickValue[1])
    local lastBrickEndTime = tonumber(brickValue[2])
    if lastBrickEndTime > brickStartTime then
        -- 还在保护期范围，申请失败
        return { 0, brickCourse, brickStartTime, lastBrickEndTime }
    end
end

-- 分配申请的授权期，为防止申请过于频繁或占用时间过长，范围在1s到30m之间
local authDuration = tonumber(ARGV[1])
if authDuration < 1000 then
    authDuration = 1000
elseif authDuration > 1800000 then
    authDuration = 1800000
end
local brickEndTime = brickStartTime + authDuration
-- 更新服务器数据
redis.call('zadd', 'tb:endTime:brickCourse', brickEndTime, brickCourse)
-- 记录一条日志信息用于事后分析
redis.call('set', 'tb:brickCourse:' .. brickCourse .. ':log', ARGV[2])

-- 成功分配授权
return { 1, brickCourse, brickStartTime, brickEndTime }