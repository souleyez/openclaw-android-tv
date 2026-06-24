plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val platformApiBaseUrl = providers
    .gradleProperty("OPENCLAW_API_BASE_URL")
    .orElse("https://oc.goods-editor.com/api")
    .get()
val openClawProjectKey = providers
    .gradleProperty("OPENCLAW_PROJECT_KEY")
    .orElse("openclaw-android-tv")
    .get()
val openClawLeaseProfile = providers
    .gradleProperty("OPENCLAW_LEASE_PROFILE")
    .orElse("server_10m")
    .get()
val openClawBootstrapPrincipalType = providers
    .gradleProperty("OPENCLAW_BOOTSTRAP_PRINCIPAL_TYPE")
    .orElse("device")
    .get()
val openClawCountryCode = providers
    .gradleProperty("OPENCLAW_COUNTRY_CODE")
    .orElse("")
    .get()
val openClawRegionCode = providers
    .gradleProperty("OPENCLAW_REGION_CODE")
    .orElse("")
    .get()
val openClawAllowBackup = providers
    .gradleProperty("OPENCLAW_ALLOW_BACKUP")
    .orElse("false")
    .map { it.toBooleanStrictOrNull()?.toString() ?: "false" }
    .get()
val openClawUsesCleartextTraffic = providers
    .gradleProperty("OPENCLAW_USES_CLEARTEXT_TRAFFIC")
    .orElse("false")
    .map { it.toBooleanStrictOrNull()?.toString() ?: "false" }
    .get()
val releaseStoreFilePath = providers
    .gradleProperty("OPENCLAW_RELEASE_STORE_FILE")
    .orElse(providers.environmentVariable("OPENCLAW_RELEASE_STORE_FILE"))
    .orNull
val releaseStorePassword = providers
    .gradleProperty("OPENCLAW_RELEASE_STORE_PASSWORD")
    .orElse(providers.environmentVariable("OPENCLAW_RELEASE_STORE_PASSWORD"))
    .orNull
val releaseKeyAlias = providers
    .gradleProperty("OPENCLAW_RELEASE_KEY_ALIAS")
    .orElse(providers.environmentVariable("OPENCLAW_RELEASE_KEY_ALIAS"))
    .orNull
val releaseKeyPassword = providers
    .gradleProperty("OPENCLAW_RELEASE_KEY_PASSWORD")
    .orElse(providers.environmentVariable("OPENCLAW_RELEASE_KEY_PASSWORD"))
    .orNull
val hasReleaseSigning = listOf(
    releaseStoreFilePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "com.openclaw.tv"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.openclaw.tv"
        minSdk = 21
        targetSdk = 34
        versionCode = 2026053010
        versionName = "0.1.13"
        manifestPlaceholders["openclawAllowBackup"] = openClawAllowBackup
        manifestPlaceholders["openclawUsesCleartextTraffic"] = openClawUsesCleartextTraffic
        buildConfigField("String", "PLATFORM_API_BASE_URL", "\"$platformApiBaseUrl\"")
        buildConfigField("String", "OPENCLAW_PROJECT_KEY", "\"$openClawProjectKey\"")
        buildConfigField("String", "OPENCLAW_LEASE_PROFILE", "\"$openClawLeaseProfile\"")
        buildConfigField("String", "OPENCLAW_COUNTRY_CODE", "\"$openClawCountryCode\"")
        buildConfigField("String", "OPENCLAW_REGION_CODE", "\"$openClawRegionCode\"")
        buildConfigField(
            "String",
            "OPENCLAW_BOOTSTRAP_PRINCIPAL_TYPE",
            "\"$openClawBootstrapPrincipalType\"",
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("factoryRelease") {
                storeFile = file(releaseStoreFilePath!!)
                storePassword = releaseStorePassword!!
                keyAlias = releaseKeyAlias!!
                keyPassword = releaseKeyPassword!!
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("factoryRelease")
            }
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
    implementation(project(":feature:appdelivery"))
    implementation(project(":feature:home"))
    implementation(project(":feature:runtime"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.google.material)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
}
