plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    namespace = "com.renhard.caloriesestimator"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.renhard.caloriesestimator"
        testApplicationId = "com.renhard.caloriesestimator.tests"
        testInstrumentationRunner = "com.renhard.caloriesestimator.TestRunner"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
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
        create("customDebugType") {
            isDebuggable = true
        }
        debug {
            enableUnitTestCoverage = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildFeatures {
        viewBinding = true
    }
    @Suppress("UnstableApiUsage")
    testOptions {
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
        }
    }
    sourceSets {
        getByName("androidTest").assets.srcDirs("src/androidTest/assets")
        getByName("test").resources.srcDirs("src/test/resources")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.material)

    implementation(libs.tensorflow.lite.delegate)
    implementation(libs.tensorflow.lite.gpu)
    implementation(libs.tensorflow.lite.gpu.api)
    implementation(libs.tensorflow.lite.api)
    implementation(libs.tensorflow.lite.support)
    implementation(libs.tensorflow.lite.tfops)
    implementation(libs.tensorflow.lite.metadata)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.glide)
    implementation(libs.mp.android.chart)

    implementation(libs.camera.core)
    implementation(libs.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.windows.manager)
    implementation(libs.androidx.paging.runtime.ktx)
    implementation(libs.google.arcore)
    implementation(libs.de.javagl)

    implementation(project(":opencv"))
    implementation(libs.core.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.junit.ktx)
    testImplementation(libs.roboelectric)
    testImplementation(libs.androidx.espresso.core)
//    androidTestImplementation(libs.mockito)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.jetbrain.stdlib)
    testImplementation(libs.jetbrain.kotlin.test)
    testImplementation(libs.jetbrain.kotlin.coroutines.cores)
    testImplementation(libs.jetbrain.kotlin.coroutines.android)
    testImplementation(kotlin("test"))

    testRuntimeOnly(files("${projectDir}/../app/src/main/java/com/renhard/caloriesestimator/util/DetectorInstanceSegment.kt"))
}