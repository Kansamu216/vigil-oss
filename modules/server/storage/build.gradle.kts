// 存储层：SQLite 迁移与仓储。
dependencies {
    api(project(":modules:shared:model"))
    implementation(libs.sqlite.jdbc)
}
