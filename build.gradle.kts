plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.7.20"
    id("org.jetbrains.intellij") version "1.13.0"
}

group = "dev.huyaro.gen"
version = "0.1.1"

repositories {
    mavenCentral()
}

dependencies {
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
    version.set("2022.1.4")
    type.set("IU") // Target IDE Platform
    downloadSources.set(true)
    updateSinceUntilBuild.set(true)
//    sandboxDir.set("${rootProject.rootDir}/idea-sandbox")
    plugins.set(
        listOf(
            "com.intellij.java",
            "com.intellij.database"
        )
    )
}


tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "11"
        targetCompatibility = "11"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "11"
    }

    patchPluginXml {
        sinceBuild.set("221")
        untilBuild.set("231.*")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        channels.set(listOf("Stable"))
        token.set(System.getenv("IDEA_PUBLISH_TOKEN"))
    }
}
