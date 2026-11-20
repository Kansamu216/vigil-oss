// 判定层：评分与处置门槛，只依赖模型。
dependencies {
    api(project(":modules:shared:model"))

    testImplementation(project(":test-support:session-harness"))
}
