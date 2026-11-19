// 存储层：SQLite、migration 与 Repository。SQLite 只允许出现在这个模块。
dependencies {
    api(project(":modules:shared:model"))
    implementation(libs.sqlite.jdbc)
}
