import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

room {
    schemaDirectory("$projectDir/schemas")
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        // Get the architecture-specific path
        val arch = when (iosTarget.name) {
            "iosArm64" -> "ios-arm64"
            "iosSimulatorArm64" -> "ios-arm64_x86_64-simulator"
            else -> "ios-arm64"
        }

        // Configure cinterop for libssh2
        iosTarget.compilations.getByName("main") {
            val libssh2 by cinterops.creating {
                // Use appropriate .def file based on target
                definitionFile = when (iosTarget.name) {
                    "iosArm64" -> project.file("src/nativeInterop/cinterop/libssh2_ios.def")
                    "iosSimulatorArm64" -> project.file("src/nativeInterop/cinterop/libssh2_sim.def")
                    else -> project.file("src/nativeInterop/cinterop/libssh2_ios.def")
                }
            }
        }

        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = false  // Use dynamic framework so libraries are embedded

            // Force-link all symbols from the static libraries
            linkerOpts(
                "-force_load", "${project.projectDir}/src/ios-libs/Frameworks/libssh2.xcframework/$arch/libssh2.a",
                "-force_load", "${project.projectDir}/src/ios-libs/Frameworks/mbedtls.xcframework/$arch/libmbedtls.a",
                "-force_load", "${project.projectDir}/src/ios-libs/Frameworks/mbedx509.xcframework/$arch/libmbedx509.a",
                "-force_load", "${project.projectDir}/src/ios-libs/Frameworks/mbedcrypto.xcframework/$arch/libmbedcrypto.a",
                "-lz"
            )
        }
    }
    
    jvm()
    
    sourceSets {
        applyDefaultHierarchyTemplate()
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)

            implementation(libs.koin.core)
            implementation(libs.koin.androidx.compose)

            implementation(libs.androidx.room.sqlite.wrapper)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            // use alpha material3 to enable expressive
            //  implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.material3)
            implementation("org.jetbrains.compose.ui:ui-backhandler:1.9.1")

            api(libs.koin.core)
            implementation(libs.koin.compose)
            implementation("io.insert-koin:koin-compose-viewmodel-navigation:4.1.1")
            implementation("io.insert-koin:koin-compose-viewmodel:4.1.1")
            implementation(libs.navigation.compose)
            implementation(libs.kotlinx.serialization.core)

            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.androidx.room.runtime)

            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
        }
        val jvmSharedMain by creating {
            dependsOn(commonMain.get())
            dependencies {
//                implementation(libs.sshj)
                implementation("com.hierynomus:sshj:0.38.0") {
                    exclude(group = "org.bouncycastle", module = "bcprov-jdk18on")
                    exclude(group = "org.bouncycastle", module = "bcpkix-jdk18on")
                    exclude(group = "org.bouncycastle", module = "bcutil-jdk18on")
                }
                implementation(libs.eddsa)
            }
        }
        androidMain.get().dependsOn(jvmSharedMain)
        jvmMain.get().dependsOn(jvmSharedMain)
    }
}

android {
    namespace = "com.github.pplong"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.github.pplong"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(compose.uiTooling)

    add("kspAndroid", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
    add("kspJvm", libs.androidx.room.compiler)
}

compose.desktop {
    application {
        mainClass = "com.github.pplong.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.github.pplong"
            packageVersion = "1.0.0"
        }
    }
}
