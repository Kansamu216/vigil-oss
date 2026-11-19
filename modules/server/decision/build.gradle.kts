// 判定层：证据分级、评分与处置门槛。只依赖模型，不依赖网络与存储。
dependencies {
    api(project(":modules:shared:model"))

    testImplementation(project(":test-support:session-harness"))
}
