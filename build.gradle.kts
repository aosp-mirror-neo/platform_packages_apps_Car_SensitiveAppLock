// Copyright (C) 2025 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

import com.google.protobuf.gradle.GenerateProtoTask
import com.google.protobuf.gradle.proto

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("aaosApps.buildLogic")
    id("com.google.protobuf")
    alias(libs.plugins.hiltAndroid)
    alias(libs.plugins.ksp)
}

// Protobuf generator
protobuf {
    protoc { artifact = libs.protobuf.protoc.get().toString() }
    generateProtoTasks {
        all().forEach { task -> task.builtins { create("java") { option("lite") } } }
    }
}

var applicationID = "com.android.car.sensitiveapplock"

android {
    namespace = applicationID

    defaultConfig {
        applicationId = applicationID
        minSdk = 34
        testInstrumentationRunner =
            "com.android.car.sensitiveapplock.instrumentation.HiltTestRunner"
        testInstrumentationRunnerArguments["clearPackageData"] = "true"
        testInstrumentationRunnerArguments["coverage"] = "true"
    }

    lint { abortOnError = false }

    sourceSets {
        sourceSets.getByName("main") {
            val kotlin =
                project.extensions
                    .getByType<org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension>()
                    .sourceSets
                    .getByName(name)
                    .kotlin
            kotlin.srcDir("src")
            java.srcDir("src/com/android/car/sensitiveapplock/metrics")
            kotlin.exclude("com/android/car/sensitiveapplock/testing/**")
            res.srcDirs("res")
            proto { setSrcDirs(listOf("proto")) }
            manifest.srcFile("AndroidManifest.xml")
        }

        val commonTestDirs =
            listOf(
                "src/com/android/car/sensitiveapplock/testing",
                "tests/src/com/android/car/sensitiveapplock/di",
            )

        // Robolectric tests
        sourceSets.getByName("test") {
            kotlin.srcDirs(
                commonTestDirs,
                "tests/src/com/android/car/sensitiveapplock/data",
                "tests/src/com/android/car/sensitiveapplock/lockscreen",
                "tests/src/com/android/car/sensitiveapplock/metrics",
                "tests/src/com/android/car/sensitiveapplock/service",
                "tests/src/com/android/car/sensitiveapplock/settings",
                "tests/src/com/android/car/sensitiveapplock/shadows",
                "tests/src/com/android/car/sensitiveapplock/suspension",
                "tests/src/com/android/car/sensitiveapplock/testing",
                "tests/src/com/android/car/sensitiveapplock/util",
            )
            resources.setSrcDirs(listOf("tests/config"))
        }

        // Instrumentation tests
        sourceSets.getByName("androidTest") {
            kotlin.srcDirs(
                commonTestDirs,
                "tests/src/com/android/car/sensitiveapplock/auth",
                "tests/src/com/android/car/sensitiveapplock/instrumentation",
            )
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }

        debug {
            enableAndroidTestCoverage = true
            enableUnitTestCoverage = true
            signingConfig = android.buildTypes["release"].signingConfig
        }
    }

    signingConfigs {
        create("platformGoogle") {
            storeFile = file(gradle.extra["platformGoogleCertPath"].toString())
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("platformAosp") {
            storeFile = file(gradle.extra["platformAospCertPath"].toString())
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    flavorDimensions += listOf("signingkey")
    productFlavors {
        if (file(gradle.extra["platformGoogleCertPath"].toString()).exists()) {
            create("platformGoogle") {
                isDefault = true
                dimension = "signingkey"
                signingConfig = signingConfigs.getByName("platformGoogle")
            }
        }
        create("platformAosp") {
            dimension = "signingkey"
            signingConfig = signingConfigs.getByName("platformAosp")
        }
    }

    testOptions {
        unitTests { isIncludeAndroidResources = true }
        animationsDisabled = true
    }
}

androidComponents {
    onVariants { variant ->
        afterEvaluate {
            val protoTask =
                project.tasks.getByName(
                    "generate" + variant.name.replaceFirstChar { it.uppercaseChar() } + "Proto"
                ) as GenerateProtoTask

            project.tasks.getByName(
                "ksp" + variant.name.replaceFirstChar { it.uppercaseChar() } + "Kotlin"
            ) {
                dependsOn(protoTask)
                (this as org.jetbrains.kotlin.gradle.tasks.AbstractKotlinCompileTool<*>).setSource(
                    protoTask.outputBaseDir
                )
            }
        }
    }
}

dependencies {
    implementation(files(gradle.extra["lib_car_system_stubs"] as String))
    implementation(libs.androidx.constraintlayout)
    compileOnly(files(gradle.extra["lib_system_stubs"] as String))

    implementation(project(":car-ui-lib"))
    implementation(project(":car-media-common"))

    implementation(libs.protobuf.kotlin.lite)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    implementation(libs.androidx.navigation.fragment.ktx)

    implementation(libs.google.tink)

    // hilt testing
    testCompileOnly(files(gradle.extra["lib_system_stubs"] as String)) // maybe not neeeded
    testImplementation(libs.androidx.annotation)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.androidx.navigation.testing)
    testImplementation(libs.androidx.test.rules)
    testImplementation(libs.androidx.test.runner)
    testImplementation(libs.google.testparameterinjector)
    testImplementation(libs.google.truth)
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.android.compiler)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockito.inline)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.robolectric)

    // instrumentation testing
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.google.truth)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.android.compiler)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.mockito.kotlin)
}

hilt {
    // Cannot do classpath aggregation
    // because manually extending Hilt_ generated classes
    enableAggregatingTask = false
}
