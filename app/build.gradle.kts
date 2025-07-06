plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.license.report)
}

android {
    namespace = "com.github.cvzi.wallpaperexport"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.github.cvzi.wallpaperexport"
        versionCode = 9
        versionName = "1.1.4"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
    }

    if (project.hasProperty("keystore")) {
        signingConfigs {
            create("release") {
                storeFile = file(project.property("keystore") as String)
                storePassword = project.property("keystorepassword") as String
                keyAlias = project.property("keystorealias") as String
                keyPassword = project.property("keystorekeypassword") as String
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    } else {
        signingConfigs {
            create("release") {
                storeFile = rootProject.file("mykey.jks")
                storePassword = "password"
                keyAlias = "key0"
                keyPassword = "password"
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isMinifyEnabled = false
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.bundles.implementation.app)
}

licenseReport {
    // Run via `gradlew licenseReleaseReport`
    generateCsvReport = false
    generateHtmlReport = true
    generateJsonReport = false
    copyHtmlReportToAssets = true
}
