// 会话测试基座，与客户端共用会话核心。
dependencies {
    api(project(":modules:shared:protocol"))
    api(project(":modules:client:agent"))
    api(project(":modules:shared:model"))
    implementation(project(":modules:shared:crypto"))
}
