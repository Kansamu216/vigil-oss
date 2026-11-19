// 运行时：把配置、会话、判定、存储装配成一条可测试的业务链路。
// 这里不出现任何加载器或 Minecraft 类型，平台适配层只调用本模块。
dependencies {
    api(project(":modules:server:session"))
    api(project(":modules:server:decision"))
    api(project(":modules:server:storage"))
    api(project(":modules:shared:protocol"))
    implementation(project(":modules:shared:crypto"))

    testImplementation(project(":test-support:session-harness"))
}
