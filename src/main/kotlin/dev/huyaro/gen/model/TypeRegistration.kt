package dev.huyaro.gen.model

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import kotlin.reflect.KClass

/**
 * @author yanghu
 * @date 2022-11-18
 * @description Storage current types mapping
 */
@State(
    name = "CodeGenX-Settings",
    storages = [Storage(value = "codegenX-settings.xml", roamingType = RoamingType.DISABLED)]
)
class TypeRegistration : PersistentStateComponent<TypeState> {

    private val defaultType = TypePair(Tag.BUILD_IN, "varchar", "java.lang.String")

    /**
     * Must be public
     * @see <a href="https://plugins.jetbrains.com/docs/intellij/persisting-state-of-components.html#implementing-the-state-class">stateClass</a>
     */
    val typeState = TypeState()

    companion object {
        fun newInstance(): TypeRegistration =
            ApplicationManager.getApplication().getService(TypeRegistration::class.java)
    }

    override fun getState(): TypeState {
        return typeState
    }

    override fun loadState(state: TypeState) {
        typeState.mapping = state.mapping
    }

    fun register(type: TypePair) =
        typeState.addType(type.jvmType, type.jdbcType)

    fun unregister(type: TypePair) =
        typeState.removeType(type.jdbcType)

    /**
     * filter type
     */
    fun getJvmType(jdbcType: String): KClass<*> =
        typeState.mapping.getOrDefault(jdbcType.lowercase(), defaultType).readJvmType()

}

