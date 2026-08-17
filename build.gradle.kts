plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.4.10"
    id("org.jetbrains.intellij.platform") version "2.18.1"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.10"
}

group = "com.freetime"
version = "1.1.0"

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
        intellijIdea("2026.2")
    }

    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    implementation("com.google.code.gson:gson:2.14.0")
    implementation("org.json:json:20231013")
}

intellijPlatform {
    pluginConfiguration {
        id = "com.freetime.maic"
        name = "Multi AI Chat"
        vendor {
            name = "Freetime Maker"
        }
        description = """
            Comprehensive Multi AI assistant for JetBrains IDEs. 
            This tool integrates major AI providers including OpenAI (GPT-4), 
            Anthropic (Claude 3), and Google Gemini into your coding workflow.
            Features include code explanation, real-time chat, and multi-provider switching.
        """.trimIndent()
    }
}
