package dev.huyaro.gen.util

import org.apache.velocity.VelocityContext
import org.apache.velocity.app.VelocityEngine
import org.apache.velocity.runtime.RuntimeConstants
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*
import kotlin.io.path.absolutePathString
import kotlin.io.path.name
import kotlin.io.path.notExists

/**
 * @author huyaro
 * @date 2022-11-15
 * @description Velocity Template Engine
 */
class VelocityTemplate {

    private lateinit var engine: VelocityEngine
    private var properties = Properties()

    init {
        properties.setProperty(
            "runtime.log.logsystem.class",
            "org.apache.velocity.runtime.log.Log4JLogChute"
        )
        properties.setProperty("resource.loader.file.unicode", StandardCharsets.UTF_8.name())
    }

    /**
     * render template file to outFile with context
     */
    fun render(outFile: Path, templateFile: Path, context: Map<String, Any>) {
        // set template resource import path
        properties.setProperty(
            RuntimeConstants.FILE_RESOURCE_LOADER_PATH,
            templateFile.parent.absolutePathString()
        )
        engine = VelocityEngine(properties)

        outFile.parent
            .takeIf { it.notExists() }
            ?.let { Files.createDirectories(it) }

        Files.newBufferedWriter(
            outFile,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE
        ).use {
            engine
                .getTemplate(templateFile.name, StandardCharsets.UTF_8.name())
                .merge(VelocityContext(context), it)
        }
    }
}