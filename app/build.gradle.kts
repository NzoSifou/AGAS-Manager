import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Signature de release : lue depuis `keystore.properties` à la racine du projet (hors dépôt, voir
 * .gitignore). Sans ce fichier, la release n'est simplement pas signée.
 */
val releaseSigning = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { file ->
    Properties().apply { file.inputStream().use { load(it) } }
}

android {
    namespace = "fr.nzosifou.agas"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "fr.nzosifou.agas"
        // Android 11 : requis pour AccessibilityService.takeScreenshot() (repli vision, phase 2).
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

    }

    signingConfigs {
        if (releaseSigning != null) {
            create("release") {
                storeFile = file(releaseSigning.getProperty("storeFile"))
                storePassword = releaseSigning.getProperty("storePassword")
                keyAlias = releaseSigning.getProperty("keyAlias")
                keyPassword = releaseSigning.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8 désactivé : il retirerait ou renommerait des classes de Kotlin et du contrat dont
            // l'Agent, chargé à la volée, a besoin (voir keepRules/rules.keep avant de l'activer).
            optimization {
                enable = false
            }
            if (releaseSigning != null) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":agent-api"))
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.phosphor.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.json)
}
