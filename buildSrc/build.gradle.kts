plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
}

apply(from = "../repositories.gradle.kts")

dependencies {
    // Gradle Plugins
    // 8.9.1 or higher, required by androidx.camera 1.6.1 which mobile_scanner pulls in; 8.10 also
    // accepts compileSdk 36, which the Flutter 3.47 AAR needs.
    implementation("com.android.tools.build:gradle:8.10.1")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.21")
}
