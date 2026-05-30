import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
plugins {
    alias(libs.plugins.android.application); alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization); alias(libs.plugins.google.ksp)
}
android {
    namespace = "com.fandata.plugin"; compileSdk = 36
    defaultConfig { applicationId = "com.fandata.plugin"; minSdk = 24; targetSdk = 36; versionCode = 1; versionName = "1.0" }
    buildFeatures { compose = true; buildConfig = true }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions { isCoreLibraryDesugaringEnabled = true; sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
androidComponents { onVariants { variant -> variant.outputs.forEach {
    val o = it as com.android.build.api.variant.impl.VariantOutputImpl; o.outputFileName = o.outputFileName.get().replace(".apk",".apk.lnrp") } } }
tasks.withType<KotlinJvmCompile>().configureEach { compilerOptions { jvmTarget.set(JvmTarget.JVM_17); freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn") } }
androidComponents { onVariants { variant -> variant.sources.manifests.addStaticManifestFile(
    layout.buildDirectory.file("generated/ksp/${variant.name}/resources/auto_register_manifest.xml").get().toString()) } }
afterEvaluate { listOf("Debug","Release").forEach { v -> tasks.findByName("process${v}MainManifest")?.dependsOn("ksp${v}Kotlin") } }
dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.kotlinx.coroutines.core); implementation(libs.androidx.runtime)
    implementation(libs.androidx.navigation.runtime.ktx); implementation(libs.androidx.foundation.layout)
    implementation(platform(libs.compose.bom)); implementation(libs.compose.material3)
    implementation(libs.kotlinx.serialization.cbor); implementation(libs.kotlinx.serialization.json)
    implementation(libs.cxhttp); implementation(libs.okhttp3.okhttp); implementation(libs.okhttp3.logging.interceptor)
    implementation(libs.jsoup); implementation(libs.gson); implementation(libs.rhino)
    implementation(project(":legado-engine"))
    compileOnly(libs.lightnovelreader.api); ksp(libs.lightnovelreader.compiler)
}
val debugHostPkg = "indi.dmzz_yyhyy.lightnovelreader.debug"
fun pluginApk(): File = File(layout.buildDirectory.asFile.get(),"outputs/apk/debug").walkTopDown().first{it.isFile&&(it.name.endsWith(".apk")||it.name.endsWith(".lnrp"))}
tasks.register("runDebugHost") { group="plugin"; dependsOn("assembleDebug"); doLast {
    val adb=listOf(androidComponents.sdkComponents.adb.get().asFile.absolutePath)+(System.getenv("ANDROID_SERIAL")?.let{listOf("-s",it)}?:emptyList())
    val src=pluginApk(); val f=if(src.name.endsWith(".apk"))src else File(src.parent,src.name.removeSuffix(".lnrp")).also{src.renameTo(it)}
    try{providers.exec{commandLine(adb+listOf("install","-r","-t",f))}.result.get()}finally{if(f!=src)f.renameTo(src)}
    providers.exec{commandLine(adb+listOf("shell","am","force-stop",debugHostPkg))}.result.get()
    providers.exec{commandLine(adb+listOf("shell","monkey","-p",debugHostPkg,"-c","android.intent.category.LAUNCHER","1"))}.result.get()
} }