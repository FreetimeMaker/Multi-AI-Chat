plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.4.10"
    id("org.jetbrains.intellij.platform") version "2.18.1"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.10"
}

group = "com.freetime"
version = "1.1.2"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    intellijPlatform {
        intellijIdea("2026.1")
    }

    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    implementation("com.squareup.okio:okio:3.7.0")
    implementation("com.google.code.gson:gson:2.14.0")
    implementation("org.json:json:20231013")
}

configurations.all {
    exclude(group = "org.ow2.asm")
    exclude(group = "org.objectweb.asm")
}

intellijPlatform {
    pluginConfiguration {
        id = "com.freetime.maic"
        name = "Multi AI Chat"

        version = project.version.toString()

        changeNotes = """
            <h3>1.1.2</h3>
            <ul>
                <li>Added support for Google Gemini 2.5 Pro and Flash.</li>
                <li>Changed the Error instead of 404 to a real message.</li>
            </ul>
        """.trimIndent()

        vendor {
            name = "Freetime Maker"
        }

        description = """
            Comprehensive Multi AI assistant for JetBrains IDEs.
            This tool integrates major AI providers including OpenAI,
            Anthropic, and Google Gemini into your coding workflow.
        """.trimIndent()
    }
}
