import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.name

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.0"
    id("org.jetbrains.intellij") version "1.15.0"
}

group = "dev.huyaro.gen"
version = "0.2.2"


// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
    version.set("2022.2.5")
    type.set("IU") // Target IDE Platform
    downloadSources.set(false)
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
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    prepareSandbox {
        val sourceRoot = "./src/main/resources/extensions/templates/jimmer"
        val templateRoot = "./build/idea-sandbox/config/extensions/dev.huyaro.gen.x/templates/jimmer"
        Files.list(Paths.get(sourceRoot))
            .filter { it.fileName.name.endsWith(".vm", true) }
            .forEach {
                copy {
                    from(it)
                    into(Paths.get(templateRoot))
                }
                // println("> Task :copy $it => ${Paths.get(templateRoot).resolve(it.fileName)} finished")
            }
    }

    patchPluginXml {
        sinceBuild.set("222")
        untilBuild.set("233.*")
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
