## 项目介绍
smart-mqtt 是用 java 语言开发的 MQTT Broker 服务，也是 smartboot 组织下首款真正意义上面向物联网的解决方案。旨在帮助企业以较低的成本快速搭建稳定、可靠的物联网服务，助力万物互联互通。
![输入图片说明](https://oscimg.oschina.net/oscnet/up-d49d180b5e95b7a421eaf86111767610fe9.png)

smart-mqtt 底层通信采用了异步非阻塞通信框架 smart-socket，现已实现了完整的 mqtt v3.1.1 协议规范，未来还将考虑支持 mqtt v5.0 及其他物联网协议。

## 项目发展
- 2018年，创建 smart-mqtt 项目，完成基本的协议编解码结构搭建。
- 2019~2021年，项目基本处于停更状态，期间重心在于提升底层通信框架 smart-socket 的性能。
- 2022年，重启 smart-mqtt。基本完成 mqtt  broker 和 client 的功能开发。

## 推荐阅读
- [加入企业支持计划](https://smartboot.gitee.io/smart-mqtt/)
- [快速上手](https://smartboot.gitee.io/smart-mqtt/quickstart.html)
- [在线服务](https://smartboot.gitee.io/smart-mqtt/service.html)
- [关于企业版](https://smartboot.gitee.io/smart-mqtt/enterprise.html)


## 功能列表
- [X] 支持MQTTv.3.1.1协议标准
- [X] 支持Qos0、Qos1、Qos2 的消息传递。
- [X] 支持遗嘱消息
- [X] 支持 retain 消息
- [X] 支持心跳消息
- [X] 插件化设计模式
- [X] mqtt client 相关功能
- [X] 优雅停机
- [X] Broker生命周期及各类事件监听
- [ ] 客户端鉴权
- [X] 支持集群部署模式
- [X] 支持通配符订阅模式
- [ ] 精准流控
- [ ] 待补充。。。

## 参考资料
1. 《MQTT协议3.1.1中文版》
2. [moquette](https://github.com/moquette-io/moquette)