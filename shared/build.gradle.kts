plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Kod wspólny dla telefonu (app) i komputera (desktop): modele sejfu, OTP, siła haseł, scalanie, protokół synchronizacji i odczyt kodów QR.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    api("com.google.zxing:core:3.5.3")
    testImplementation("junit:junit:4.13.2")
}
