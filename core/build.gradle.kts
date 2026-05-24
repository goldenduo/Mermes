import java.io.FileOutputStream
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.mermes.core"
    compileSdk = 35
    defaultConfig {
        minSdk = 24

        testApplicationId = "com.mermes"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["uninstall"] = "false"
        consumerProguardFiles("consumer-rules.pro")

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        }

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DPROJECT_ROOT_DIR=${rootProject.projectDir.absolutePath}"
                )
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    sourceSets {
        getByName("main") {
            // Bootstrap zip files for each architecture
            assets.srcDirs("src/main/assets")
        }
    }
}

// Task to verify bootstrap files exist
tasks.register("packageBootstrap") {
    doFirst {
        val bootstrapDir = rootProject.file("download/mermes_bootstrap")
        if (!bootstrapDir.exists() || bootstrapDir.listFiles()?.none { it.name.endsWith(".zip") } == true) {
            error("mermes_bootstrap directory is empty or missing! Please run bootstrap download script first.")
        }
        println("Bootstrap packages checked and verified.")
    }
}

// Task to package deb files to architecture zip files in download/mermes_deb
tasks.register("packageDebs") {
    doFirst {
        val debDir = rootProject.file("download/mermes_deb")
        if (!debDir.exists()) {
            println("mermes_deb directory does not exist!")
            return@doFirst
        }

        val archMap = mapOf(
            "arm64" to "aarch64",
            "arm32" to "arm",
            "x86" to "i686",
            "x64" to "x86_64"
        )

        archMap.forEach { (srcDirName, targetArch) ->
            val srcDir = File(debDir, srcDirName)
            val zipFile = File(debDir, "debs-$targetArch.zip")
            if (srcDir.exists() && srcDir.isDirectory) {
                val debFiles = srcDir.listFiles()?.filter { it.name.endsWith(".deb") } ?: emptyList()
                if (debFiles.isNotEmpty()) {
                    val maxLastModified = debFiles.maxOf { it.lastModified() }
                    if (!zipFile.exists() || zipFile.lastModified() < maxLastModified) {
                        println("Packaging debs for $targetArch into ${zipFile.name}...")
                        FileOutputStream(zipFile).use { fos ->
                            ZipOutputStream(fos).use { zos ->
                                debFiles.forEach { file ->
                                    val entryName = file.name.replace(":", "_")
                                    zos.putNextEntry(ZipEntry(entryName))
                                    file.inputStream().use { it.copyTo(zos) }
                                    zos.closeEntry()
                                }
                            }
                        }
                        println("Successfully packaged ${zipFile.name}")
                    } else {
                        println("${zipFile.name} is up-to-date.")
                    }
                }
            }
        }
    }
}

// Ensure native configuration and builds depend on our packaging tasks
tasks.configureEach {
    if (name.startsWith("buildCMake") || name.startsWith("configureCMake") || name.startsWith("externalNativeBuild")) {
        dependsOn("packageBootstrap")
        dependsOn("packageDebs")
    }
}

// Task to generate libmermes-bootstrap.so and copy to a friendly output directory
tasks.register("buildBootstrapSo") {
    dependsOn("externalNativeBuildDebug")
    doLast {
        val buildDir = layout.buildDirectory.asFile.get()
        val outDir = File(buildDir, "outputs/native_libs/bootstrap")
        outDir.mkdirs()
        val searchDir = File(buildDir, "intermediates")
        var count = 0
        val abis = listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        if (searchDir.exists()) {
            searchDir.walkTopDown().filter { it.name == "libmermes-bootstrap.so" }.forEach { file ->
                val abiName = file.parentFile.name
                if (abiName in abis) {
                    val target = File(File(outDir, abiName), file.name)
                    target.parentFile.mkdirs()
                    file.copyTo(target, overwrite = true)
                    println("Gathered libmermes-bootstrap.so for $abiName")
                    count++
                }
            }
        }
        if (count == 0) {
            println("No libmermes-bootstrap.so found. Did the native build run successfully?")
        } else {
            // Divide by 2 because files are found in both cmake intermediates and merged_native_libs
            val uniqueCount = count / 2
            println("Successfully gathered libmermes-bootstrap.so for $uniqueCount architectures to: ${outDir.absolutePath}")
        }
    }
}

// Task to generate libmermes-deb.so and copy to a friendly output directory
tasks.register("buildDebSo") {
    dependsOn("externalNativeBuildDebug")
    doLast {
        val buildDir = layout.buildDirectory.asFile.get()
        val outDir = File(buildDir, "outputs/native_libs/deb")
        outDir.mkdirs()
        val searchDir = File(buildDir, "intermediates")
        var count = 0
        val abis = listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        if (searchDir.exists()) {
            searchDir.walkTopDown().filter { it.name == "libmermes-deb.so" }.forEach { file ->
                val abiName = file.parentFile.name
                if (abiName in abis) {
                    val target = File(File(outDir, abiName), file.name)
                    target.parentFile.mkdirs()
                    file.copyTo(target, overwrite = true)
                    println("Gathered libmermes-deb.so for $abiName")
                    count++
                }
            }
        }
        if (count == 0) {
            println("No libmermes-deb.so found. Did the native build run successfully?")
        } else {
            val uniqueCount = count / 2
            println("Successfully gathered libmermes-deb.so for $uniqueCount architectures to: ${outDir.absolutePath}")
        }
    }
}

dependencies {
    implementation(project(":common"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("net.java.dev.jna:jna:5.16.0")
    implementation("org.apache.commons:commons-compress:1.27.1")
    implementation("org.tukaani:xz:1.10")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
