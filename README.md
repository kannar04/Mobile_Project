# ANILOVE Android app

Open this folder in Android Studio and sync Gradle, then run the app.

The repository pins the Gradle daemon to a full JDK 21 toolchain. On the first
command-line build, Gradle may download that JDK automatically. Let that download
finish; Android's `JdkImageTransform` requires the JDK tools `jlink`, `javac`, and
`jmod`, which are absent from a JRE-only installation.

Build the Firebase-connected app from PowerShell:

    .\gradlew.bat :app:assembleSpark

If an older checkout has already started Gradle with another Java runtime, stop
that daemon once after pulling the current configuration, then build again:

    .\gradlew.bat --stop
    .\gradlew.bat :app:assembleSpark

Do not commit `local.properties`; each developer's Android Studio creates it with
that machine's Android SDK path.
