group = "fuzzer"
version = "1.0-SNAPSHOT"

repositories {
    jcenter()
    mavenCentral()
    google()
}

plugins {
    kotlin("jvm")
    id("antlr")
}

val kotlinVersion = "1.3.60"
val ktorVersion = "1.1.4"
val logbackVersion = "1.2.3"

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-client-apache:$ktorVersion")
    implementation("io.ktor:ktor-jackson:$ktorVersion")
    implementation("io.ktor:ktor-html-builder:$ktorVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
//    implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime:0.14.0")
    implementation("org.jetbrains.exposed:exposed:0.13.2")
    implementation("org.apache.commons:commons-csv:1.1")
    implementation("org.apache.commons:commons-text:1.8")
    implementation("org.jsoup:jsoup:1.11.3")
    antlr("org.antlr:antlr4:4.5")
//    implementation("log4j:log4j:1.2.17")
    implementation("org.slf4j:slf4j-simple:1.7.29")
    implementation("org.apache.commons:commons-csv:1.1")
    implementation("org.kodein.di:kodein-di-generic-jvm:6.4.1")
    implementation(project(":include:kotlin-compiler"))
    implementation(project(":idea:idea-core"))
    implementation(project(":compiler:tests-common"))
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.10.+")
    implementation(files("libs/kotlin-grammar-parser-0.1.jar"))
    implementation("org.antlr:antlr4-runtime:4.4.5")
    compile(intellijDep())

    Platform[192].orHigher {
        compile(intellijPluginDep("java"))
    }

    compile(intellijPluginDep("gradle"))

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
    testImplementation("org.knowm.xchart:xchart:3.5.4")
}

tasks.generateGrammarSource {
    outputDirectory = File("${project.buildDir}/generated-src/antlr/main/org/antlr/parser/antlr4")
    arguments = arguments + listOf("-package", "org.antlr.parser.antlr4")
}