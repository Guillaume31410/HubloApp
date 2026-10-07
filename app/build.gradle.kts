plugins {
    id("com.android.application")
}

// Numéro de build fourni par GitHub Actions : chaque APK a un versionCode
// croissant, ce qui permet de l'installer par-dessus le précédent.
val numeroBuild = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "fr.perso.hublot"
    compileSdk = 37

    defaultConfig {
        applicationId = "fr.perso.hublot"
        minSdk = 26
        targetSdk = 37
        versionCode = numeroBuild
        versionName = "1.0.$numeroBuild"
    }

    signingConfigs {
        // Clé de debug commitée dans le dépôt : même signature à chaque build,
        // donc mise à jour de l'APK sans désinstaller (les paramètres sont gardés).
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Seule dépendance externe, et uniquement pour les tests.
    testImplementation("junit:junit:4.13.2")
}
