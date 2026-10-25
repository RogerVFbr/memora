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
android {
    namespace = "br.com.memora"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = "br.com.memora"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-alpha.1"
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            resValue("string", "app_name", "Memora Dev")
        }
        release { resValue("string", "app_name", "Memora") }
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
        inputs.files(checkout.resolve("package.json"), checkout.resolve("package-lock.json"))
        val version = variant.outputs.single().versionName.get()
        inputs.property("appVersion", version)
        environment("VITE_APP_VERSION", version)
        doFirst {
            // AGP assigns the generated source directory after task registration.
            commandLine("npm", "run", "build", "--workspace", "@memora/tv", "--", "--outDir", outputDirectory.get().asFile.absolutePath)
        }
    }
    variant.sources.assets?.addGeneratedSourceDirectory(buildTv, BuildTvAssets::outputDirectory)
}
