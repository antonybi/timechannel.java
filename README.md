# timechannel

[![Build Status](https://app.travis-ci.com/antonybi/timechannel.java.svg?branch=master)](https://app.travis-ci.com/antonybi/timechannel.java)
[![Coverage Status](https://coveralls.io/repos/github/antonybi/timechannel.java/badge.svg?branch=master)](https://coveralls.io/github/antonybi/timechannel.java?branch=master)
[![Maven central](https://maven-badges.herokuapp.com/maven-central/io.github.antonybi/timechannel/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.github.antonybi/timechannel)
[![License: MIT](https://img.shields.io/badge/license-MIT-green.svg)](https://opensource.org/licenses/MIT)


## Introduction

目前分布式ID生成算法的主流依然是snowflake，比较知名的实现有twitter官方版本、sonyflake、美团Leaf。但snowflake在工程实现上，存在一些比较棘手的问题，如时钟回拨、位如何分配等。

故个人重新设计了一个高可靠的`轻量级`实现，命名为`timechannel`，同时也避免了时钟回拨问题、支持更灵活的位分配。在本地4C16G VM中压测，将序列号分配12bit，QPS压测结果达50w/s，可满足绝大多数应用场景。

## Comparison

|     | timechannel | leaf snowflake | leaf segment |
| --- | --- | --- | --- |
| 依赖组件 | SDK<br>Redis集群（推荐sentinel） | SDK<br>Leaf Server<br>Zookeeper集群 | SDK<br>Leaf Server<br>Mysql集群（半同步） |
| 实现复杂度 | 简单，不足500行源码 | 简单，依赖twitter snowflake的实现 | 相对复杂 |
| 性能  | 高   | 高   | 中   |
| 支持worker上限 | 无限，需配置多space | 1024 | 无限，配置不同应用 |
| 支持bit位分配配置 | 是   | 否   | 否   |
| 时钟回拨问题 | 无   | 有   | 无   |
| 潜在风险 | 运行时强依赖Redis | 时钟回拨会造成服务暂停 | 运行时强依赖Mysql |

## Quick Start

### 项目集成

本项目基于spring boot构建，按下列方式即可使用：

#### 1\. 在pom中加入依赖

```xml
<dependency>
    <groupId>io.github.antonybi</groupId>
    <artifactId>timechannel</artifactId>
    <version>1.1.0</version>
</dependency>
```

#### 2\. 在application.yml中编写必要的参数配置
```yaml
spring:
  application:
    name: demo-service

guid:
  redis:
    host: 127.0.0.1
    port: 6379
```

#### 2\. 启动类增加引用

```java
@SpringBootApplication(scanBasePackages = "timechannel")
public class DemoApp {

    public static void main(String[] args) {
        SpringApplication.run(DemoApp.class, args);
    }

}
```

#### 3\. 代码中直接使用

```java
@Service
public class DemoService {

    @Resource
    private Guid guid;

    public long nextId() {
        return guid.nextId();
    }

}
```

## Design

### Design Thinking
受snowflake算法的启发，我们把64个bit位拆分成两个部分，一个部分是时间，另外一个部分是序列号，这样就可以看成一个二维的空间。
然后我们将序列号bit位再分成两个部分，前部是频道，后部是序号，那么每个频道都会包含一组私有的序号。
这个结构看起来就像是把时间轴线上有很多频道，每个guid就是一个平面上的点，所以按二维表命名为timechannel。

![image](https://github.com/antonybi/timechannel.java/blob/master/doc/time-channel.svg)

### Concept

| 名词  | 中文  | 含义  | 用法  |
| --- | --- | --- | --- |
| space | 空间  | 同一空间内保持guid全局唯一 | 当实例数超过group+channel所承载的范围，可按场景划分空间，同时生成多组guid |
| group | 组   | 将频道分成多个组 | 每个机房部署一个redis集群，每个集群一个group |
| channel | 频道  | 实例的编号，与worker概念一致 | 每个实例会占用一个频道 |
| lease | 租约  | 对一个频道占用时间的合约 | 实例启动时会占用一个频道，并不断进行续期 |

### bit位的划分

默认划分如下：

![image](https://github.com/antonybi/timechannel.java/blob/master/doc/bits-division.svg)

项目中允许自由配置分段，在实现中增加了group的概念，但默认为0 bit。完整bit划分如下：

`1-bit unused` \+ `42-bit timestamp` \+ `0-bit group` \+ `11-bit channel` \+ `12-bit sequence` = 64

### 算法的实现

#### 租约的申请与续期

为了保证guid生成的效率，项目中采用异步线程的提前续期，续期间隔为ttl的1/2

![image](https://github.com/antonybi/timechannel.java/blob/master/doc/timechannel-generate.svg)

#### guid的生成

此项与大多数实现都相似，只是这里用了租约，简化了这部分的实现

![image](https://github.com/antonybi/timechannel.java/blob/master/doc/timechannel-lease.svg)

### 配置介绍

本项目基于spring boot，在application.properties中可配置以下参数：

| 配置项 | 含义  | 默认值 | 备注  |
| --- | --- | --- | --- |
| guid.redis.host | redis地址 | -   | 【必填】 |
| guid.redis.port | redis端口 | 6379 |     |
| guid.space | 空间  | 0   | 当实例数接近频道总数，可以按业务场景划分到不同空间 |
| guid.ttl | 每次申请授权续期的时长 | 10m | 此值越大，频道回收时间会越慢，但Redis异常时应用可持续工作越久 |
| guid.group.id | 频道分组编号，从0开始计数 | 0   | 多机房建议独立部署redis集群，并分配不同group |
| guid.bits.group | 频道分组bit位数 | 0   | 与redis集群数量保持一致 |
| guid.bits.channel | 频道占用的bit位数，默认共2048个 | 11  | 同一个space下，该值保持一致 |
| guid.bits.sequence | 序列号占用的bit位数，默认速度1024/ms | 10  | 同一个space下，该值保持一致 |

注： channel和sequence所占用的bit位不能多于22位，避免时间戳的值域空间太小。

### 运行检查

```shell
# 查看channel的总数
zcard space:0:expiryTime:channel

# 查看目前可用的channel总数，时间戳换成当前时间
zcount space:0:expiryTime:channel 0 1661079389000

# 查看正在被占用的channel
zrangebyscore space:0:expiryTime:channel 1661079389000 9999999999999 WITHSCORES

# 根据上条命令查到的频道号查看最后一次申请日志
get space:0:channel:0:log
```

## FAQ

### 原则类问题

#### Q1:增加space概念的意义在哪？

snowflake采用long型来做guid，虽说有64bit可以用来做分配，但为了便于划分，其实存在非常严重的id浪费。而可能有些场景需要消耗的id非常多，比如批处理作业，这就导致bit位不论怎么划分都会顾此失彼。但我们实际业务场景中所需要的guid，其实并不需要全局唯一的，只是在特定场景下能做到全局唯一即可。因此，我们很容易就可以做到将id生成按场景拆分成多个，单一场景能支持几百个实例完全够用，进而彻底避免支持实例数有限制的问题。

#### Q2：有了space，还有必要再增加一个group的概念吗？

space解决的是id不够过多的实例分配的问题，这个的前提是略微破坏了guid的定义，就是在不同场景下生成了同样的id并没有关系。这看起来很像是数据库中每个表都有id，他们都是从1开始自增，但是业务上拿来做关联关系并不会有问题。
但在不同机房部署相同的服务，情况就变得不一样了，这会出现两边是由同样的场景，那么id也不能重复。为了保证guid服务的可用性，肯定是每个机房单独部署一个redis集群，这样机房之间的网络中断了，业务依然可以正常运行。所以，我们就需要一个group来占用一定的bit位，与机房对应起来，这样就可以实现这样的效果。

#### Q3：介绍中多次强调时钟回拨问题，不管到底行不行？

首先，我们必需强调一个问题，id一旦在预期外产生了重复，这个结果对业务而言都是灾难性的破坏，当系统有多处关联依赖也会导致修复会非常复杂。因此这个是必需要考虑的。
目前能看到几种解决方案：

1.  直接停止进入等待，直到本地时间追上，但业务暂停时间长了可能会有严重后果
2.  关闭NTP，这更不是一个在生产环境可行的解决方案，很多业务逻辑都是强依赖时钟的
3.  将每个worker（即timechannel中的channel）上次所用到的时间记录下来，下次分配的时候参考

我认为只有3才是可接受的策略。实际实现的时候，采用了租约续期的思路。系统中没有使用会与NTP同步的逻辑时钟，而是使用了`单调时钟`（Java语言中是System.nanoTime()），只记录程序运行的时间累计。这样就绕开了时钟回拨的问题。

#### Q4:选择运行时强依赖redis，这合理吗？

Q2已经回答了需要一个存储模块来记录上一个应用消耗到什么时间。项目中为了避免实现的复杂度，就用了租约续期的思路来实现。可能会在应用下限后租约不立刻释放会有一点id的浪费，但真正要注意的是，频道数量要比实例数略多一些，避免这种切换时未及时释放的问题。
至于为什么存储选用了redis，主要是因为性能，这里用了`zset`的数据结构来高效查找可用的channel。其次，它的高可用部署也比较简单，在业内用得也比较普遍。

### 权衡类问题

#### Q5:为什么选择用一个更复杂的SDK，而避免多一个server的架构？

简单来说，多一个组件就多一层复杂度，进而多一层风险。对于guid这种对全局系统都有影响的依赖而言，我们自然希望是风险越小越好。因此，单一SDK的会比c/s结构会更可靠，而且用起来也更简单。当然，带来的负面问题就是没有集中的server可以进行管控，只能通过redis命令查看分配的结果。因此，生产环境很有必要增加一些监控脚本来分析channel的消耗情况，比如可以参考“运行检查”的命令并增减监控告警。

#### Q6:为什么在时间戳上采用ms为单位？

诚然使用s作为单位会更有效的应对突增流量，这个和令牌桶非常类似，ms就是一个粒度更小的令牌桶。但是guid在实际使用中我们还有一个重要的诉求——递增。虽然snowflake只能做到趋势递增，但我们也应尽量减小乱序的数量，比如mysql主键就是在物理存储上保持有序。因为bit位划分后所能生成的id数量非常大，在10bit就有100w/s，如果出现乱序其实也是差别很大的。因此这里依然使用了ms作为单位。

#### Q7:为什么时间戳不支持一个起始时间，以扩大值域？

很多snowflake算法的实现都会采用这个设计，这个直接好处就是避免了1970/01/01以来的值域浪费，可以让这个算法可以用的时间更久。但这个起始时间就是一个需要大家都要知晓的事件，比如用到guid的时间进行分表就要加回起始时间，如果有些新人并不知情，就会出现混乱。另外，当系统中和其他系统系统交互，大家的起始时间并不同，也会让处理的复杂度提升，容易出现错误。而这个id的问题一旦出错，前面也说过，很可能是灾难性的。因此我认为不必为这点id浪费去承担这样的风险。

#### Q8:为什么时间戳默认占用比常规snowflake多了一个bit位？

因为41bit代表ms，时间只能用69年，也就是到2039年。如果十年前确实不用纠结，但是现在来看时间太近了，还是让出多1bit，我们就可以用到2109年，这就完全不用担心了。

#### Q9:为什么不支持同时占用多个频道，甚至支持动态扩展频道？

我确实想过这么去设计，但是会让项目复杂度又增大了，而且这种场景非常少见。因为正常系统而言，100w/s的id生成速度是远高于业务处理速度的。如果压测场景之类非常高id消耗的场景，那么建议直接换个space，调大sequence的bit位就可以应对。

#### Q10:为什么提供string型的guid？

在能接受存储的略微扩大和性能的略微下降，很多时候大家会选择可读性更好的带业务含义id。通常是在id签名增加场景的缩写字母，id本身带有时间属性，可用于按时间分库分表。当然这就不能用long来表示了，存储就变成了string，那么我也顺便把原来sequence转成了16进制略微压缩了一点字符串长度。至于为啥没有用64进制这种更大压缩，只是我觉得16进制数字看着还有点感觉，没过分追求压缩率。

#### Q11:为什么使用lua脚本没用evalsha命令？

如果细心的你可能就会发现，项目中调用lua是直接用了解释执行的eval命令，而不是编译后的evalsha。原因是兼容性更好且不影响性能。
因为项目中续期都是异步线程提前执行的，对性能没要求。另外，evalsha需要你提前在redis上编译好，再配置对应的sha到程序中，也很麻烦，如果用了twemproxy这样的代理，或者低版本的redis，还不支持这个命令。

## 注意事项

1.  考虑到guid生成对系统运行至关重要，而本方案又强依赖Redis，故推荐Redis用sentinel模式部署集群，并且独占。
2.  已有生产数据的情况下，轻易不要调整bits的分配，初始化的队列长度做动态调整可能会导致重复分配id。如需调整，建议等到所有channel均过期后，统一使用一个新的space，或者del原space的zset。
3.  为了尽量保证系统的可用性，在极端情况下Redis集群不可访问，SDK会认为一直占有channel继续工作，但此时需避免应用启停，先恢复Redis集群。但channel申请是依据LRU策略，也最大程度以免意外启停导致错误。

## License

Released under the [MIT License](https://github.com/antonybi/timechannel.java/blob/master/LICENSE)