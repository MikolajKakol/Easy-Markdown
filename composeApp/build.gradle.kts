import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.roborazzi)
}

/** JavaFX native classifier of the machine running the build; the desktop app is packaged for it. */
val javafxPlatform: String = run {
    val os = System.getProperty("os.name").lowercase()
    val arm = System.getProperty("os.arch") in setOf("aarch64", "arm64")
    when {
        "mac" in os -> if (arm) "mac-aarch64" else "mac"
        "windows" in os -> "win"
        else -> if (arm) "linux-aarch64" else "linux"
    }
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(libs.markdown.renderer.m3)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }
        val desktopMain by getting {
            // Shared with Android: mermaid.js page assets and the code font.
            resources.srcDir("src/androidMain/assets")
            resources.srcDir("src/androidMain/res/font")
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.jna)
                // JavaFX WebView renders mermaid diagrams. Its POMs pick the native classifier with Maven
                // profiles that Gradle ignores, so the modules are listed explicitly for the build host.
                for (module in listOf("base", "graphics", "controls", "media", "web", "swing")) {
                    implementation("org.openjfx:javafx-$module:${libs.versions.javafx.get()}:$javafxPlatform") {
                        isTransitive = false
                    }
                }
            }
        }
        androidUnitTest.dependencies {
            implementation(libs.junit)
            implementation(libs.robolectric)
            implementation(libs.androidx.test.core)
            implementation(libs.roborazzi)
            implementation(libs.roborazzi.compose)
            implementation(libs.compose.ui.test.junit4)
        }
    }
}

android {
    namespace = "io.github.mikolajkakol.easymarkdown"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.github.mikolajkakol.easymarkdown"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "0.1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    signingConfigs {
        // Shared debug key committed to the repo, so CI builds can be installed over each other.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName("debug")
        }
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Registers the empty ComponentActivity used by createComposeRule() in snapshot tests.
    debugImplementation(libs.compose.ui.test.manifest)
}

compose.desktop {
    application {
        mainClass = "io.github.mikolajkakol.easymarkdown.MainKt"
        jvmArgs += "-Dapple.awt.application.appearance=system"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "Easy Markdown"
            packageVersion = "1.0.0"
            description = "Markdown viewer with Mermaid diagrams"
            modules("java.net.http", "java.prefs", "jdk.jsobject", "jdk.unsupported", "jdk.unsupported.desktop", "jdk.xml.dom")

            macOS {
                bundleID = "io.github.mikolajkakol.easymarkdown"
                appCategory = "public.app-category.productivity"
                iconFile.set(project.file("icons/EasyMarkdown.icns"))
                infoPlist {
                    // Declares the app as a viewer of markdown files, so Finder and `open` can use it as the
                    // default. The imported type maps all common markdown extensions to the system UTI.
                    extraKeysRawXml = """
                        <key>CFBundleDocumentTypes</key>
                        <array>
                            <dict>
                                <key>CFBundleTypeName</key>
                                <string>Markdown document</string>
                                <key>CFBundleTypeRole</key>
                                <string>Viewer</string>
                                <key>LSHandlerRank</key>
                                <string>Default</string>
                                <key>LSItemContentTypes</key>
                                <array>
                                    <string>net.daringfireball.markdown</string>
                                </array>
                            </dict>
                        </array>
                        <key>UTImportedTypeDeclarations</key>
                        <array>
                            <dict>
                                <key>UTTypeIdentifier</key>
                                <string>net.daringfireball.markdown</string>
                                <key>UTTypeDescription</key>
                                <string>Markdown document</string>
                                <key>UTTypeConformsTo</key>
                                <array>
                                    <string>public.plain-text</string>
                                </array>
                                <key>UTTypeTagSpecification</key>
                                <dict>
                                    <key>public.filename-extension</key>
                                    <array>
                                        <string>md</string>
                                        <string>markdown</string>
                                        <string>mdown</string>
                                        <string>mkd</string>
                                    </array>
                                    <key>public.mime-type</key>
                                    <array>
                                        <string>text/markdown</string>
                                        <string>text/x-markdown</string>
                                    </array>
                                </dict>
                            </dict>
                        </array>
                    """.trimIndent()
                }
            }
        }
    }
}
