plugins { alias(libs.plugins.android.library); alias(libs.plugins.kotlin.serialization) }
android {
    namespace = "io.legado.engine"; compileSdk = 36
    defaultConfig { minSdk = 24 }
    compileOptions { isCoreLibraryDesugaringEnabled = true; sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.rhino); implementation(libs.jsoup); implementation(libs.json.path)
    implementation(libs.okhttp3.okhttp); implementation(libs.okhttp3.logging.interceptor)
    implementation(libs.gson); implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core); implementation(libs.commons.text)
    compileOnly("androidx.annotation:annotation:1.9.1")
}