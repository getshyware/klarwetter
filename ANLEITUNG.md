# Klarwetter – APK bauen

1. Android Studio öffnen → **File › Open** → den Ordner `Klarwetter` wählen.
2. Warten, bis „Gradle Sync“ fertig ist (beim ersten Mal lädt Android Studio das SDK 36 und Gradle).
   Schlägt Android Studio ein Update des Android Gradle Plugins vor, darfst du es annehmen.
3. **Build › Build App Bundle(s) / APK(s) › Build APK(s)**.
4. Die Datei liegt danach unter `app/build/outputs/apk/debug/app-debug.apk`.
5. APK aufs Handy kopieren und öffnen (Installation aus „unbekannten Quellen“ einmal erlauben),
   oder das Handy per USB verbinden und in Android Studio auf ▶ (Run) klicken.

Widget: lange auf den Startbildschirm tippen → Widgets → Klarwetter, oder in der App
„Widget zum Startbildschirm hinzufügen“ antippen.

Datenschutz: keine Bibliotheken von Dritten, keine Analyse, keine Werbung, keine Standortberechtigung.
Einzige Berechtigung: Internet (für die Wetterdaten von Open-Meteo.com, CC BY 4.0).
