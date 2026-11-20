// 客户端会话核心，不依赖加载器或 Minecraft 类型。
dependencies {
    api(project(":modules:shared:protocol"))
    api(project(":modules:shared:model"))
    implementation(project(":modules:shared:crypto"))
}
