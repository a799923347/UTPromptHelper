plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.10"
    id("org.jetbrains.intellij") version "1.15.0"
}

group = "com.ut.prompt"
version = "1.0"

repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.aliyun.com/repository/public")
    }
    maven {
        url = uri("https://repo1.maven.org/maven2")
    }
    maven {
        url = uri("https://cache-redirector.jetbrains.com/www.jetbrains.com/intellij-repository")
    }
}

intellij {
    version.set("2023.3.6")
    type.set("IU") // Target IDE Platform - IntelliJ IDEA Ultimate
    plugins.set(listOf("java"))
    updateSinceUntilBuild.set(false)
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
}

kotlin {
    jvmToolchain(17)
}
