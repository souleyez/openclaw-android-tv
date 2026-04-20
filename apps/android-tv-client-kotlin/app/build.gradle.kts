plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val platformApiBaseUrl = providers
    .gradleProperty("OPENCLAW_API_BASE_URL")
    .orElse("http://1.12.246.48/api")
    .get()
val openClawProjectKey = providers
    .gradleProperty("OPENCLAW_PROJECT_KEY")
    .orElse("openclaw-android-tv")
    .get()
val openClawLeaseProfile = providers
    .gradleProperty("OPENCLAW_LEASE_PROFILE")
    .orElse("client_short")
    .get()
val openClawBootstrapPrincipalType = providers
    .gradleProperty("OPENCLAW_BOOTSTRAP_PRINCIPAL_TYPE")
    .orElse("device")
    .get()

android {
    namespace = "com.openclaw.tv"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.openclaw.tv"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "PLATFORM_API_BASE_URL", "\"$platformApiBaseUrl\"")
        buildConfigField("String", "OPENCLAW_PROJECT_KEY", "\"$openClawProjectKey\"")
        buildConfigField("String", "OPENCLAW_LEASE_PROFILE", "\"$openClawLeaseProfile\"")
        buildConfigField(
            "String",
            "OPENCLAW_BOOTSTRAP_PRINCIPAL_TYPE",
            "\"$openClawBootstrapPrincipalType\"",
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:network"))
    implementation(project(":core:storage"))
    implementation(project(":feature:bootstrap"))
    implementation(project(":feature:home"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.google.material)

    testImplementation(libs.junit4)

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
}
