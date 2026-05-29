import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val keystoreProperties = Properties().apply {
    val keystoreFile = rootProject.file("keystore.properties")
    if (keystoreFile.exists()) {
        keystoreFile.reader(Charsets.UTF_8).use { load(it) }
    }
}

// Carregar propriedades da versão
val versionPropsFile = file("version.properties")
val versionProps = Properties().apply {
    if (versionPropsFile.exists()) {
        versionPropsFile.reader(Charsets.UTF_8).use { load(it) }
    } else {
        put("major", "0")
        put("minor", "0")
        put("patch", "1")
        put("build", "1")
        versionPropsFile.writer(Charsets.UTF_8).use { store(it, "Initial Version Properties") }
    }
}

val verMajor = versionProps.getProperty("major").toInt()
val verMinor = versionProps.getProperty("minor").toInt()
val verPatch = versionProps.getProperty("patch").toInt()
val verBuild = versionProps.getProperty("build").toInt()

val calculatedVersionName = "$verMajor.$verMinor.$verPatch"
val calculatedVersionCode = verBuild

android {
    namespace = "br.com.bibliafalada.agendadevocional"
    compileSdk = 37

    defaultConfig {
        applicationId = "br.com.bibliafalada.agendadevocional"
        minSdk = 27
        targetSdk = 35
        versionCode = calculatedVersionCode
        versionName = calculatedVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystoreProperties.containsKey("storeFile")) {
            create("release") {
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            signingConfigs.findByName("release")?.let {
                signingConfig = it
            }
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

tasks.register("incrementVersion") {
    group = "versioning"
    description = "Increments the version patch number and build number or sets a specific version using -PnovaVersao=X.Y.Z"
    doLast {
        val major: Int
        val minor: Int
        val patch: Int
        var build = versionProps.getProperty("build").toInt()

        if (project.hasProperty("novaVersao")) {
            val target = project.property("novaVersao") as String
            val parts = target.split(".")
            if (parts.size >= 3) {
                major = parts[0].toIntOrNull() ?: versionProps.getProperty("major").toInt()
                minor = parts[1].toIntOrNull() ?: versionProps.getProperty("minor").toInt()
                patch = parts[2].toIntOrNull() ?: versionProps.getProperty("patch").toInt()
                build += 1
            } else {
                major = versionProps.getProperty("major").toInt()
                minor = versionProps.getProperty("minor").toInt()
                patch = versionProps.getProperty("patch").toInt()
            }
        } else {
            val curMajor = versionProps.getProperty("major").toInt()
            val curMinor = versionProps.getProperty("minor").toInt()
            var curPatch = versionProps.getProperty("patch").toInt()

            curPatch += 1
            build += 1

            patch = if (curPatch > 9) 0 else curPatch
            val tempMinor = if (curPatch > 9) curMinor + 1 else curMinor
            minor = if (tempMinor > 9) 0 else tempMinor
            major = if (tempMinor > 9) curMajor + 1 else curMajor
        }

        versionProps.setProperty("major", major.toString())
        versionProps.setProperty("minor", minor.toString())
        versionProps.setProperty("patch", patch.toString())
        versionProps.setProperty("build", build.toString())

        versionPropsFile.writer(Charsets.UTF_8).use { versionProps.store(it, "Version update") }
        println("Versão atualizada para: $major.$minor.$patch (Build: $build)")
    }
}

// Executa o incremento de versão de forma automática APENAS ao gerar o AAB (bundleRelease)
// O APK (assembleRelease) usará a versão atual sem alterá-la para evitar duplicações
tasks.matching { 
    it.name.startsWith("bundleRelease") 
}.configureEach {
    dependsOn(tasks.named("incrementVersion"))
}