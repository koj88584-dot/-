-- 限流Lua脚本
-- KEYS[1]: 限流key
-- ARGV[1]: 限制次数
-- ARGV[2]: 时间窗口（秒）

local key = KEYS[1]
local limit = tonumber(ARGV[1])
local time = tonumber(ARGV[2])

-- 获取当前计数
local current = tonumber(redis.call('get', key) or "0")

-- 判断是否超过限制
if current + 1 > limit then
    return 0
else
    -- 自增
    redis.call('incrby', key, 1)
    -- 设置过期时间（仅在key不存在时设置）
    if current == 0 then
        redis.call('expire', key, time)
    end
    return current + 1
end
