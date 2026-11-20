// 客户端会话核心：验签、信任锚、报告加密与 AUTH 帧构造。
// 不依赖任何加载器或 Minecraft 类型，平台适配层只提供玩家与 Mod 清单。
dependencies {
    api(project(":modules:shared:protocol"))
    api(project(":modules:shared:model"))
    implementation(project(":modules:shared:crypto"))
}
