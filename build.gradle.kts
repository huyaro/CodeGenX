import com.jetbrains.plugin.structure.base.utils.simpleName
import java.nio.file.Files
import java.nio.file.Paths

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.8.21"
    id("org.jetbrains.intellij") version "1.13.3"
}

group = "dev.huyaro.gen"
version = "0.1.4"


repositories {
    mavenCentral()
}

dependencies {
}

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
            .filter { it.fileName.simpleName.endsWith(".vm", true) }
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
        untilBuild.set("232.*")
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
