import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}
// Note: KSP plugin should be applied at the project/pluginManagement level
// if you want to enable annotation processing. Configure KSP with a version
// compatible with your Kotlin version (see notes).

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
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }
    
    jvm()
    
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }
    
    sourceSets {
        all {
            languageSettings.optIn("kotlin.time.ExperimentalTime")
        }
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            // Navigation for Compose Multiplatform
            implementation("org.jetbrains.androidx.navigation:navigation-compose:2.9.1")
            implementation("org.jetbrains.compose.material:material:1.9.3")
            implementation("org.jetbrains.compose.material:material-icons-core:1.7.3")
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
            implementation(libs.kotlinx.coroutines.core)
            // Kotlinx serialization (used by typed navigation routes)
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.9.0")
            
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.okio)
            implementation(libs.multiplatform.markdown.renderer.m3)
            implementation(libs.multiplatform.markdown.renderer.coil3)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor)
        }
        
        val nonWebMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation(libs.androidx.datastore.preferences.core)
                implementation(libs.androidx.room.runtime)
                implementation(libs.sqlite.bundled)
            }
        }

        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        androidMain.get().dependsOn(nonWebMain)

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        iosMain.get().dependsOn(nonWebMain)

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.ktor.client.java)
            implementation(libs.google.api.client)
            implementation(libs.google.api.services.drive)
        }
        jvmMain.get().dependsOn(nonWebMain)

        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

android {
    namespace = "io.github.smiling_pixel"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.github.smiling_pixel"
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
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspJvm", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
    debugImplementation(compose.uiTooling)
}

compose.desktop {
    application {
        mainClass = "io.github.smiling_pixel.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "io.github.smiling_pixel"
            packageVersion = "1.0.0"
        }
    }
}
