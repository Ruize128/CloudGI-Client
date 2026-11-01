plugins { id("com.android.library") }

android {
    namespace = "com.limelight"
    compileSdk = 36
    ndkVersion = "28.2.13676358"
    defaultConfig {
        minSdk = 26
        ndk { abiFilters += "arm64-v8a" }
        buildConfigField("boolean", "ROOT_BUILD", "false")
        buildConfigField("String", "APPLICATION_ID", "\"io.github.ruize128.cloudgi.client\"")
        resValue("string", "app_label", "CloudGI Client")
        resValue("string", "app_label_root", "CloudGI Client")
        externalNativeBuild { ndkBuild { arguments += "PRODUCT_FLAVOR=nonRoot" } }
        consumerProguardFiles("../third-party/moonlight-android/app/proguard-rules.pro")
    }
    buildFeatures { buildConfig = true; resValues = true }
    sourceSets["main"].apply {
        java.srcDir("../third-party/moonlight-android/app/src/main/java")
        res.srcDir("../third-party/moonlight-android/app/src/main/res")
    }
    externalNativeBuild { ndkBuild { path = file("../third-party/moonlight-android/app/src/main/jni/Android.mk") } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging { resources.excludes += "/META-INF/*.md" }
}

dependencies {
    api("org.bouncycastle:bcprov-jdk15on:1.70")
    api("org.bouncycastle:bcpkix-jdk15on:1.70")
    implementation("org.jcodec:jcodec:0.2.3")
    api("com.squareup.okhttp3:okhttp:3.12.13")
    implementation("com.squareup.okio:okio:1.17.5")
    api("org.jmdns:jmdns:3.5.7")
    implementation("com.github.cgutman:ShieldControllerExtensions:1.0.1")
}
