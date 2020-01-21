
//import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

//buildscript {
//    repositories {
//        jcenter()
//        mavenCentral()
//    }
//    dependencies {
//        classpath("org.springframework.boot:spring-boot-gradle-plugin:2.2.0.RELEASE")
//    }
//}

plugins {
    java
    application
    val kotlinVersion = "1.3.21"
    id("org.springframework.boot") version "2.2.0.RELEASE"
    id("org.jetbrains.kotlin.jvm") version kotlinVersion
    id("org.jetbrains.kotlin.plugin.spring") version kotlinVersion
    id("org.jetbrains.kotlin.plugin.jpa") version kotlinVersion
    id("io.spring.dependency-management") version "1.0.6.RELEASE"
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

repositories {
    jcenter()
    mavenCentral()
//    maven("http://mvnrepository.com/artifact")
//    flatDir {
//        dirs("lib")
//    }
}

dependencies {
//    implementation("jfugue:jfugue:5.0.9")
    implementation(files("lib/jfugue-5.0.9.jar"))
    implementation(files("lib/TarsosDSP-2.4.jar"))


    implementation("org.springframework.boot:spring-boot-starter-web") {
        exclude(module = "spring-boot-starter-logging")
    }
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
//    implementation("org.springframework.boot:spring-boot-starter-actuator")

//    implementation("org.springframework.boot:spring-boot-starter-web:2.2.2.RELEASE")
//    implementation("org.springframework.boot:spring-boot-starter-actuator:2.2.2.RELEASE")
    implementation("com.google.guava:guava:28.0-jre")
    implementation("com.lmax:disruptor:3.4.2")
//    implementation("com.j256.simplejmx:simplejmx:1.17")
//    implementation("org.springframework:spring-context:4.0.2.RELEASE")
//    implementation("org.jminix:jminix:1.2.0")
//    implementation("io.hawt:hawtio-embedded:2.8.0")
//    implementation("io.hawt:hawtio-log:2.8.0")


    testImplementation("junit:junit:4.12")
}

application {
    mainClassName = "com.disactor.pitches.Pitches"
}
