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
                    "-DANDROID_STL=c++_shared"
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

// Task to copy bootstrap zip files to cpp directory for native build
tasks.register("copyBootstrapFiles") {
    doFirst {
        val bootstrapDir = rootProject.file("download/mermes_bootstrap")
        val cppDir = file("src/main/cpp")

        if (bootstrapDir.exists()) {
            bootstrapDir.listFiles()?.filter { it.name.endsWith(".zip") }?.forEach { file ->
                val target = File(cppDir, file.name)
                if (!target.exists() || target.lastModified() < file.lastModified()) {
                    file.copyTo(target, overwrite = true)
                    println("Copied ${file.name} to cpp directory")
                }
            }
        }
    }
}

// Ensure bootstrap files are copied before native build
tasks.configureEach {
    if (name.startsWith("buildCMake") || name.startsWith("configureCMake")) {
        dependsOn("copyBootstrapFiles")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("net.java.dev.jna:jna:5.16.0")
    implementation("org.apache.commons:commons-compress:1.27.1")
    implementation("org.tukaani:xz:1.10")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
