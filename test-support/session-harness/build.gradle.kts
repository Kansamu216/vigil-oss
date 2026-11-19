// 参考客户端：只实现“客户端侧应做的事”（验签、包裹会话密钥、加密报告），
// 供会话测试与后续真实客户端 Agent 共用同一套密码学口径。
dependencies {
    api(project(":modules:shared:protocol"))
    api(project(":modules:shared:model"))
    implementation(project(":modules:shared:crypto"))
}
