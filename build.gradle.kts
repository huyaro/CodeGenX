
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.name

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.21"
    id("org.jetbrains.intellij") version "1.16.1"
}

group = "dev.huyaro.gen"
version = "0.2.6"

repositories {
    mavenCentral()
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
    version.set("2023.2.8")
    type.set("IU") // Target IDE Platform
    downloadSources.set(false)
    updateSinceUntilBuild.set(true)
//    sandboxDir.set("${rootProject.rootDir}/idea-sandbox")
    plugins.set(
        listOf(
            "org.jetbrains.kotlin",
            "com.intellij.java",
            "com.intellij.database"
        )
    )
}


tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    prepareSandbox {
        val sourceRoot = "./src/main/resources/extensions/templates"
        val templateRoot = "./build/idea-sandbox/config/extensions/dev.huyaro.gen.x/templates"
        Files.walk(Paths.get(sourceRoot), 2)
            .filter { it.fileName.name.endsWith(".vm", true) }
            .forEach {
                copy {
                    from(it)
                    into(Paths.get(templateRoot).resolve(Paths.get(sourceRoot).relativize(it)).parent)
                }
                println("> Task :copy $it => ${Paths.get(templateRoot).resolve(it.fileName)} finished")
            }
    }

    patchPluginXml {
        sinceBuild.set("232")
        untilBuild.set("243.*")
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

