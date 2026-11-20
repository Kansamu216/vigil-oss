// 会话层：挑战、握手、保活、超时与限流。
dependencies {
    api(project(":modules:shared:protocol"))
    api(project(":modules:shared:model"))
    implementation(project(":modules:shared:crypto"))

    testImplementation(project(":test-support:session-harness"))
}
