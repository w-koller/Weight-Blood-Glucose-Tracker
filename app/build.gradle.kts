import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import javax.inject.Inject

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

/**
 * Copies the licence and notice files that libraries ship inside their archives into the app's assets, so
 * About › Licences can show them. The Apache License asks that these travel with the app.
 */
abstract class CollectLicencesTask : DefaultTask() {
    @get:InputFiles abstract val archives: ConfigurableFileCollection
    @get:InputFile abstract val appLicence: RegularFileProperty
    @get:OutputDirectory abstract val output: DirectoryProperty
    @get:Inject abstract val archiveOperations: ArchiveOperations

    @TaskAction
    fun collect() {
        val folder = output.get().dir("licences").asFile
        folder.deleteRecursively()
        folder.mkdirs()
        appLicence.get().asFile.copyTo(File(folder, "weight-glucose-tracker-LICENSE.txt"))
        val wanted = setOf("LICENSE", "LICENSE.txt", "NOTICE", "NOTICE.txt", "THIRD_PARTY_NOTICE.txt")
        archives.files.forEach { archive ->
            // litertlm-android-0.18.0.aar is named litertlm-android.
            val library = archive.nameWithoutExtension.replace(Regex("""-\d[^-]*$"""), "")
            archiveOperations.zipTree(archive).visit {
                val atTop = relativePath.segments.size == 1 || relativePath.pathString.startsWith("META-INF/")
                if (!isDirectory && atTop && name in wanted) copyTo(File(folder, "$library-${name.removeSuffix(".txt")}.txt"))
            }
        }
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
        versionCode = 7
        versionName = "1.0.0-rc.3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        // ABI filters are set per build type: a build type's filters are added to defaultConfig's, not put in their place.
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            // Phones are arm64; x86_64 is kept for emulators in debug builds.
            ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        }
        release {
            isMinifyEnabled = false
            // Phones with 8 GB or more are arm64; leaving out the emulator libraries keeps the download small.
            ndk { abiFilters += listOf("arm64-v8a") }
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

// The archives whose licence and notice files are shown in About › Licences.
val licenceArchives: Configuration = configurations.detachedConfiguration(
    dependencies.create("com.google.ai.edge.litertlm:litertlm-android:${libs.versions.litertlm.get()}@aar"),
    dependencies.create("org.apache.commons:commons-compress:${libs.versions.commonsCompress.get()}"),
).apply { isTransitive = false }

androidComponents {
    onVariants { variant ->
        val collectLicences = tasks.register<CollectLicencesTask>("collect${variant.name.replaceFirstChar { it.uppercase() }}Licences") {
            archives.from(licenceArchives)
            appLicence.set(rootProject.layout.projectDirectory.file("LICENSE"))
        }
        variant.sources.assets?.addGeneratedSourceDirectory(collectLicences, CollectLicencesTask::output)
    }
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
