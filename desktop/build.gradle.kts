import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.compose")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("com.google.zxing:core:3.5.3")
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    testImplementation("junit:junit:4.13.2")
}

compose.desktop {
    application {
        mainClass = "pl.wolter.ostoja.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "Ostoja"
            // Każde nowe wydanie musi mieć wyższy numer - inaczej instalator nie zaktualizuje zainstalowanej wersji
            // (ten sam numer = ten sam ProductCode w MSI). upgradeUuid zostaje na zawsze ten sam.
            packageVersion = "1.2.9"
            description = "Ostoja - password vault and 2FA codes synced with your phone" // ASCII: WiX (MSI) nie przyjmuje polskich znaków w stronie kodowej en-us
            vendor = "xwolter"
            modules("jdk.httpserver", "java.naming", "jdk.crypto.ec", "jdk.unsupported")
            windows {
                iconFile.set(project.file("icon.ico"))
                menu = true
                shortcut = true
                menuGroup = "Ostoja"
                upgradeUuid = "6f3b1c52-0e1d-4b7f-9a63-3c1f0f5e0a71"
            }
        }
    }
}
