plugins {
    id("com.android.application")
}

android {
    namespace = "de.klarwetter.app"
    compileSdk = 36

    defaultConfig {
        // Eindeutige Kennung im Play Store/F-Droid – auf eigener Domain (getshyware.com).
        applicationId = "com.getshyware.klarwetter"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Signiert wird über Build › Generate Signed App Bundle / APK mit dem eigenen Schlüssel.
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Kein von Google signierter Abhängigkeits-Block im APK (Vorgabe von F-Droid).
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}
// Keine Abhängigkeiten: keine Analyse-, Werbe- oder Tracking-Bibliotheken.
