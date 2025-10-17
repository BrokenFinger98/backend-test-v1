tasks.jar {
    enabled = true
}

tasks.bootJar {
    enabled = false
}

dependencies {
    implementation(projects.modules.domain)
    implementation("org.springframework:spring-context")
    implementation("org.springframework:spring-tx")
}
