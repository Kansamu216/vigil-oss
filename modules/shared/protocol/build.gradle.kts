// 协议层负责信封与消息负载编解码；数据模型本身放在 shared/model，
// 保证模型不含任何网络实现，也避免 model 反向依赖 protocol。
dependencies {
    api(project(":modules:shared:model"))
}
