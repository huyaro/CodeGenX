package dev.huyaro.gen.util

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.util.io.isDirectory
import dev.huyaro.gen.model.GeneratorOptions
import org.jetbrains.jps.model.java.JavaSourceRootType
import java.nio.file.Files
import java.nio.file.Paths

/**
 * @author yanghu
 * @date 2022-11-21
 * @description Function details...
 */

/**
 * build generator options
 */
fun initOptionsByModule(module: Module): GeneratorOptions {
    val username = System.getProperty("user.name")
    val sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots(JavaSourceRootType.SOURCE)

    // guess root package
    val sourceRootDir = Paths.get(sourceRoots[0].path)
    val pkgRootDir = Files.walk(sourceRootDir, 5).filter { it.isDirectory() && Files.list(it).count() > 1 }
        .min { p1, p2 -> p1.toString().length - p2.toString().length }.get()
    val pkgRoot = sourceRootDir.relativize(pkgRootDir).joinToString(separator = ".")

    return GeneratorOptions(
        activeModule = module, author = username, rootPackage = pkgRoot, outputDir = sourceRoots[0].path
    )
}