import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinxSerialization)
    `maven-publish`
}

group = "me.znotchill"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val ktor_version = "3.4.2"

kotlin {
    macosArm64()
    linuxArm64()
    linuxX64()
    mingwX64()

    targets.withType<KotlinNativeTarget>().configureEach {
        binaries {
            executable {
                entryPoint = "me.znotchill.lime.main"
            }
        }
    }

    sourceSets {
        nativeMain.dependencies {
            implementation(libs.kotlinxSerializationJson)
            implementation("io.ktor:ktor-server-core:${ktor_version}")
            implementation("io.ktor:ktor-server-cio:${ktor_version}")
            implementation("io.ktor:ktor-network:${ktor_version}")
            implementation("io.ktor:ktor-network-tls:${ktor_version}")
            implementation("io.ktor:ktor-client-core:${ktor_version}")
            implementation("com.akuleshov7:ktoml-core:0.7.1")
            implementation("com.akuleshov7:ktoml-file:0.7.1")
            implementation("co.touchlab:kermit:2.0.4")
            implementation(kotlin("reflect"))
            implementation("dev.whyoleg.cryptography:cryptography-core:0.6.0")
            implementation("dev.whyoleg.cryptography:cryptography-provider-optimal:0.6.0")
        }

        val mingwX64Main by getting {
            dependencies { implementation("io.ktor:ktor-client-winhttp:$ktor_version") }
        }
        val linuxX64Main by getting {
            dependencies { implementation("io.ktor:ktor-client-curl:$ktor_version") }
        }
        val linuxArm64Main by getting {
            dependencies { implementation("io.ktor:ktor-client-curl:$ktor_version") }
        }
        val macosArm64Main by getting {
            dependencies { implementation("io.ktor:ktor-client-darwin:$ktor_version") }
        }
    }
}




publishing {
    publications.withType<MavenPublication>().configureEach {
        artifactId = artifactId
            .replace("macosarm64", "macos-arm64")
            .replace("linuxarm64", "linux-arm64")
            .replace("linuxx64", "linux-x64")
            .replace("mingwx64", "windows")
    }
    repositories {
        maven {
            name = "znotchill"
            url = uri("https://repo.znotchill.me/repository/maven-releases/")
            credentials {
                username = findProperty("zRepoUsername") as String? ?: System.getenv("MAVEN_USER")
                password = findProperty("zRepoPassword") as String? ?: System.getenv("MAVEN_PASS")
            }
        }
        mavenLocal()
    }
}