plugins {
    kotlin("jvm") version "1.9.21"
    application
}


group = "io.goji"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}



dependencies {
// Vert.x core dependencies
    implementation(platform("io.vertx:vertx-stack-depchain:4.5.11"))
    implementation("io.vertx:vertx-core")
    implementation("io.vertx:vertx-web")
    implementation("io.vertx:vertx-lang-kotlin")
    implementation("io.vertx:vertx-web-client")

    // Kotlin standard library and coroutines
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.21")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.7.3")
    implementation("io.vertx:vertx-lang-kotlin-coroutines")

    // JSON processing
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.3")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("ch.qos.logback:logback-classic:1.4.14")

    // Testing
    testImplementation("io.vertx:vertx-junit5")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("io.goji.waifu.MainKt")
}
