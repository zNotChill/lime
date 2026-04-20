import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinxSerialization)
}

group = "me.user"
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
            implementation("com.akuleshov7:ktoml-core:0.7.1")
            implementation("com.akuleshov7:ktoml-file:0.7.1")
            implementation("co.touchlab:kermit:2.0.4")
            implementation(kotlin("reflect"))
        }
    }
}
