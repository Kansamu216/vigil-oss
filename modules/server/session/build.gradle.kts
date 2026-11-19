// 会话层：挑战、握手、保活、超时与限流。依赖共享协议与加密原语，
// 不依赖任何加载器或 Minecraft 类型，保证可在纯 JVM 下测试。
dependencies {
    api(project(":modules:shared:protocol"))
    api(project(":modules:shared:model"))
    implementation(project(":modules:shared:crypto"))

    testImplementation(project(":test-support:session-harness"))
}
