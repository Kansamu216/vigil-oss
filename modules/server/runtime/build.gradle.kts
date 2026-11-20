dependencies {
    api(project(":modules:server:session"))
    api(project(":modules:server:decision"))
    api(project(":modules:server:storage"))
    api(project(":modules:shared:protocol"))
    implementation(project(":modules:shared:crypto"))

    testImplementation(project(":test-support:session-harness"))
}
