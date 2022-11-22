package dev.huyaro.gen.model

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.jetbrains.rd.util.first
import java.io.Serializable
import kotlin.reflect.KClass

/**
 * @author yanghu
 * @date 2022-11-18
 * @description Function details...
 */
@State(
    name = "GeneratorXSettings",
    storages = [Storage(value = "generatorX-settings.xml", roamingType = RoamingType.DISABLED)]
)
class TypeRegistration : PersistentStateComponent<TypeMapping>, Serializable {

    private var typeMap = TypeMapping()
    private val defaultType = mapOf("varchar" to TypePair(Tag.BUILD_IN, "varchar", String::class))

    companion object {
        fun getInstance(): TypeRegistration {
            return ApplicationManager.getApplication().getService(TypeRegistration::class.java)
        }
    }

    override fun getState(): TypeMapping {
        return typeMap
    }

    override fun loadState(state: TypeMapping) {
        typeMap.typePairs = state.typePairs
    }

    fun register(type: TypePair) {
        typeMap.addType(type.javaType, type.jdbcType)
    }

    fun unregister(type: TypePair) {
        typeMap.removeType(type.jdbcType)
    }

    fun resetTypes() {
        typeMap.initTypes()
    }

    /**
     * filter type
     */
    fun getJvmType(jdbcType: String): KClass<*> {
        return typeMap.typePairs
            .filter { it.key == jdbcType.lowercase() }
            .ifEmpty { defaultType }
            .first().value
            .javaType
    }
}