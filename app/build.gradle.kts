import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

/**
 * Downloads a file once and checks it against its published SHA-256, so a third-party binary can be
 * used without committing it to the repository.
 */
abstract class VerifiedDownloadTask : DefaultTask() {
    @get:Input abstract val url: Property<String>
    @get:Input abstract val sha256: Property<String>
    @get:OutputFile abstract val output: RegularFileProperty

    @TaskAction
    fun download() {
        val target = output.get().asFile
        target.parentFile.mkdirs()
        val partial = File(target.path + ".part")
        URI(url.get()).toURL().openStream().use { input -> partial.outputStream().use { input.copyTo(it) } }
        val digest = MessageDigest.getInstance("SHA-256")
        partial.inputStream().use { input ->
            val buffer = ByteArray(1 shl 20)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (actual != sha256.get()) {
            partial.delete()
            throw GradleException("Checksum mismatch for ${url.get()}: expected ${sha256.get()}, got $actual")
        }
        Files.move(partial.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}

// sherpa-onnx runs the optional natural voices. It is published as an AAR on GitHub, not on Maven.
val sherpaOnnxVersion = "1.13.8"
val sherpaOnnxAar = layout.buildDirectory.file("third-party/sherpa-onnx-static-link-onnxruntime-$sherpaOnnxVersion.aar")
val downloadSherpaOnnx = tasks.register<VerifiedDownloadTask>("downloadSherpaOnnx") {
    url.set("https://github.com/k2-fsa/sherpa-onnx/releases/download/v$sherpaOnnxVersion/sherpa-onnx-static-link-onnxruntime-$sherpaOnnxVersion.aar")
    sha256.set("b22c3fc1b6a45666d28892bb2f7694beeb77a8362d7ebd77c1a5431ec9435471")
    output.set(sherpaOnnxAar)
}

// Release signing comes from environment variables so the keystore never lives in the repository.
val releaseKeystore: String? = System.getenv("WGT_KEYSTORE_PATH")

android {
    namespace = "io.github.wkoller.glucosetracker"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.wkoller.glucosetracker"
        minSdk = 31
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Phones are arm64; x86_64 is kept for emulators in debug builds.
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("WGT_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("WGT_KEY_ALIAS")
                keyPassword = System.getenv("WGT_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            // Phones with 8 GB or more are arm64; leaving out the emulator libraries keeps the download small.
            ndk {
                abiFilters.clear()
                abiFilters += listOf("arm64-v8a")
            }
            if (releaseKeystore != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("screenshots.dir", layout.buildDirectory.dir("screenshots").get().asFile.absolutePath)
            it.maxHeapSize = "2g"
        }
    }
}

kotlin {
    jvmToolchain(17)
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.work.runtime.ktx)
    implementation(libs.litertlm.android)
    implementation(files(sherpaOnnxAar).builtBy(downloadSherpaOnnx))
    implementation(libs.commons.compress)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
