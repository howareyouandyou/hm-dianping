--1.参数列表
--1.1优惠券id
local voucherId = ARGV[1]
--1.2用户id
local userId = ARGV[2]

--2.数据id
--2.1库存id
local stockKey='seckill:stock:' .. voucherId
--2.2订单id
local orderKey='seckill:order:' .. voucherId

--3.脚本业务
--3.1判断库存是否充足 get stockKey
if(tonumber(redis.call('get',stockKey)) <= 0) then
    --3.2库存不足，返回1
    return 1
end
--3.2 判断用户是否下单 SISMEMBER orderKey userId（判断集合orderKey中有没有这个userId）
if(redis.call('sismember',orderKey,userId)==1) then
    --3.3 存在，说明是重复下单，返回2
    return 2
end
--3.4扣库存 incrby stockKey -1
redis.call('incrby',stockKey,-1)
--3.5下单（保存用户） sadd orderKey userId
redis.call('sadd',orderKey,userId)
--成功返回0
return 0

