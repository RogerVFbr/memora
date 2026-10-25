import java.util.Properties
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.OutputDirectory

abstract class BuildTvAssets : Exec() {
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
val releaseVersion = Properties().apply {
    rootDir.parentFile.parentFile.resolve("android-release.properties").inputStream().use { load(it) }
}

android {
    namespace = "br.com.memora"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = releaseVersion.getProperty("applicationId")
        minSdk = 30
        targetSdk = 35
        versionCode = releaseVersion.getProperty("versionCode").toInt()
        versionName = releaseVersion.getProperty("versionName")
    }
    signingConfigs {
        create("release") {
            storeFile = providers.environmentVariable("MEMORA_KEYSTORE_FILE").orNull?.let { file(it) }
            storePassword = providers.environmentVariable("MEMORA_SIGNING_PASSWORD").orNull
            keyAlias = providers.environmentVariable("MEMORA_KEY_ALIAS").orNull
            keyPassword = providers.environmentVariable("MEMORA_SIGNING_PASSWORD").orNull
            storeType = "PKCS12"
        }
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            resValue("string", "app_name", "Memora Dev")
        }
        release {
            resValue("string", "app_name", "Memora")
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint {
        warningsAsErrors = true
        // Versions are deliberately pinned; upgrades require compatibility review.
        disable += "GradleDependency"
    }
}
dependencies {
    implementation("androidx.webkit:webkit:1.12.1")
    testImplementation("junit:junit:4.13.2")
}
dependencyLocking { lockAllConfigurations() }

// Generate isolated assets per variant; Gradle owns the frontend build dependency.
androidComponents.onVariants { variant ->
    val buildTv = tasks.register<BuildTvAssets>("buildTv${variant.name.replaceFirstChar { it.uppercase() }}") {
        val checkout = rootDir.parentFile.parentFile
        workingDir(checkout)
        outputDirectory.set(layout.buildDirectory.dir("generated/tvAssets/${variant.name}"))
        inputs.files(fileTree(checkout.resolve("packages/presentation")) { exclude("node_modules/**") })
        inputs.files(fileTree(checkout.resolve("apps/tv")) { exclude("node_modules/**", "dist/**") })
        inputs.files(checkout.resolve("package.json"), checkout.resolve("package-lock.json"), checkout.resolve("android-release.properties"))
        val version = variant.outputs.single().versionName.get()
        inputs.property("appVersion", version)
        environment("VITE_APP_VERSION", version)
        // Frontend tooling has no use for the native signing credentials.
        environment.remove("MEMORA_SIGNING_PASSWORD")
        environment.remove("MEMORA_KEYSTORE_FILE")
        environment.remove("MEMORA_KEY_ALIAS")
        doFirst {
            // AGP assigns the generated source directory after task registration.
            commandLine("npm", "run", "build", "--workspace", "@memora/tv", "--", "--outDir", outputDirectory.get().asFile.absolutePath, "--emptyOutDir")
        }
    }
    variant.sources.assets?.addGeneratedSourceDirectory(buildTv, BuildTvAssets::outputDirectory)
}
