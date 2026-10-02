plugins {
    alias(libs.plugins.android.library)
}

/**
 * Contrat entre AGAS Manager et AGAS Agent. Le Manager l'embarque ; l'Agent (dépôt AGAS-Agent)
 * compile contre ces sources sans les embarquer : à l'exécution, il utilise la copie du Manager.
 */
android {
    namespace = "fr.nzosifou.agas.agent.api"
    compileSdk {
        version = release(37)
    }
    defaultConfig {
        minSdk = 30
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // Fournie par le Manager à l'exécution ; explicite pour le build de l'Agent, qui désactive la
    // dépendance Kotlin par défaut.
    compileOnly(libs.kotlin.stdlib)
}
