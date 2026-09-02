import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Единственный источник правды для scope модуля — файл `app/module-scope.txt`.
 * Из него генерируются: массив ресурсов `module_scope` (легаси-формат Xposed),
 * `META-INF/xposed/scope.list` (новый формат LSPosed) и объект `ModuleScope` для UI.
 */
abstract class GenerateXposedMetadata : DefaultTask() {

    @get:Input
    abstract val scopePackages: ListProperty<String>

    @get:Input
    abstract val entryClass: Property<String>

    @get:Input
    abstract val moduleId: Property<String>

    @get:Input
    abstract val moduleName: Property<String>

    @get:Input
    abstract val moduleVersion: Property<String>

    @get:Input
    abstract val moduleVersionCode: Property<Int>

    @get:Input
    abstract val moduleAuthor: Property<String>

    @get:Input
    abstract val moduleDescription: Property<String>

    @get:Input
    abstract val minApi: Property<Int>

    @get:Input
    abstract val generatedPackage: Property<String>

    @get:OutputDirectory
    abstract val resDir: DirectoryProperty

    @get:OutputDirectory
    abstract val javaResourcesDir: DirectoryProperty

    @get:OutputDirectory
    abstract val kotlinDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val packages = scopePackages.get()

        val values = resDir.get().asFile.resolve("values").apply { mkdirs() }
        values.resolve("module_scope.xml").writeText(
            buildString {
                appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
                appendLine("<!-- Сгенерировано из app/module-scope.txt, не редактировать -->")
                appendLine("<resources>")
                appendLine("""    <string-array name="module_scope">""")
                packages.forEach { appendLine("        <item>$it</item>") }
                appendLine("    </string-array>")
                appendLine("</resources>")
            }
        )

        val xposedDir = javaResourcesDir.get().asFile.resolve("META-INF/xposed").apply { mkdirs() }
        xposedDir.resolve("java_init.list").writeText(entryClass.get() + "\n")
        xposedDir.resolve("scope.list").writeText(packages.joinToString(separator = "\n", postfix = "\n"))
        xposedDir.resolve("module.prop").writeText(
            buildString {
                appendLine("id=${moduleId.get()}")
                appendLine("name=${moduleName.get()}")
                appendLine("version=${moduleVersion.get()}")
                appendLine("versionCode=${moduleVersionCode.get()}")
                appendLine("author=${moduleAuthor.get()}")
                appendLine("description=${moduleDescription.get()}")
                appendLine("minApi=${minApi.get()}")
            }
        )

        val pkg = generatedPackage.get()
        val sourceDir = kotlinDir.get().asFile.resolve(pkg.replace('.', '/')).apply { mkdirs() }
        sourceDir.resolve("ModuleScope.kt").writeText(
            buildString {
                appendLine("package $pkg")
                appendLine()
                appendLine("// Сгенерировано из app/module-scope.txt, не редактировать")
                appendLine("object ModuleScope {")
                appendLine("    val packages: List<String> = listOf(")
                packages.forEach { appendLine("        \"$it\",") }
                appendLine("    )")
                appendLine("}")
            }
        )
    }
}

val moduleScopePackages: Provider<List<String>> =
    providers.fileContents(layout.projectDirectory.file("module-scope.txt")).asText
        .map { text ->
            text.lineSequence()
                .map { it.substringBefore('#').trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .sorted()
                .toList()
        }
        .orElse(emptyList())

val moduleVersionName = "1.0.0"
val moduleVersionCode = 1
val hookEntryClass = "com.skb8.vivotool.core.HookEntry"

val generateXposedMetadata = tasks.register<GenerateXposedMetadata>("generateXposedMetadata") {
    group = "xposed"
    description = "Генерирует метаданные Xposed/LSPosed из app/module-scope.txt"

    scopePackages.set(moduleScopePackages)
    entryClass.set(hookEntryClass)
    moduleId.set("vivo-tool")
    moduleName.set("Vivo Tool")
    moduleVersion.set(moduleVersionName)
    moduleVersionCode.set(moduleVersionCode)
    moduleAuthor.set("skb8")
    moduleDescription.set("Модульный набор хуков для прошивок Vivo (LSPosed)")
    minApi.set(93)
    generatedPackage.set("com.skb8.vivotool.core")

    resDir.set(layout.buildDirectory.dir("generated/xposed/res"))
    javaResourcesDir.set(layout.buildDirectory.dir("generated/xposed/resources"))
    kotlinDir.set(layout.buildDirectory.dir("generated/xposed/kotlin"))
}

android {
    namespace = "com.skb8.vivotool"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.skb8.vivotool"
        minSdk = 27
        targetSdk = 35
        versionCode = moduleVersionCode
        versionName = moduleVersionName
    }

    signingConfigs {
        val storeFilePath = System.getenv("VIVO_KEYSTORE_FILE")
        if (!storeFilePath.isNullOrBlank() && file(storeFilePath).exists()) {
            create("release") {
                storeFile = file(storeFilePath)
                storePassword = System.getenv("VIVO_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("VIVO_KEY_ALIAS")
                keyPassword = System.getenv("VIVO_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // Модуль активно использует рефлексию из процессов чужих приложений,
            // поэтому обфускация и шринкинг отключены.
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets["main"].res.srcDir(generateXposedMetadata.flatMap { it.resDir })
    sourceSets["main"].resources.srcDir(generateXposedMetadata.flatMap { it.javaResourcesDir })
    sourceSets["main"].kotlin.srcDir(generateXposedMetadata.flatMap { it.kotlinDir })

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
    }

    packaging {
        resources.excludes += setOf(
            "DebugProbesKt.bin",
            "kotlin-tooling-metadata.json",
            "META-INF/*.version",
            "kotlin/**"
        )
    }
}

base.archivesName.set("vivo-tool-$moduleVersionName")

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

tasks.named("preBuild") {
    dependsOn(generateXposedMetadata)
}

dependencies {
    compileOnly(libs.xposed.api)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}
