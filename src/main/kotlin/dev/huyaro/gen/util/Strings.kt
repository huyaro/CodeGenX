package dev.huyaro.gen.util

import dev.huyaro.gen.model.Operator
import dev.huyaro.gen.model.OptPosition
import dev.huyaro.gen.model.OptTarget
import dev.huyaro.gen.model.StrategyRule
import java.util.*

/**
 * @author huyaro
 * @date 2022-11-21
 * @description Function details...
 */
/**
 * underline to camelcase
 */
fun toCamelCase(str: String, firstUpper: Boolean = false, delimiter: String = "_"): String {
    var nextToUpper = false
    val builder = StringBuilder()
    for (i in str.indices) {
        val current = str.substring(i, i + 1)
        when {
            current == delimiter -> nextToUpper = true
            nextToUpper -> {
                builder.append(current.uppercase())
                nextToUpper = false
            }

            else -> builder.append(current)
        }
    }
    val finalStr = builder.toString()
    return if (firstUpper) finalStr.replaceFirstChar { it.uppercase() } else finalStr
}

/**
 * camelcase to underline
 */
fun toUnderline(str: String): String {
    val trimStr = str.trim()
    if (trimStr.isEmpty()) return ""
    val strList = mutableListOf<String>()
    var i = 1
    var j = 0
    while (i < trimStr.length) {
        if (trimStr[i] in 'A'..'Z') {
            strList.add(trimStr.substring(j, i))
            j = i
        }
        i++
    }
    strList.add(trimStr.substring(j))
    return strList.joinToString("_") { it.lowercase(Locale.getDefault()) }
}

/**
 * trim all space and split
 */
fun trimAndSplit(value: String): List<String> {
    val valueList = emptyList<String>()
    if (value.isEmpty() || value.isBlank()) {
        return valueList
    }
    return value.replace("\\s".toRegex(), "").split(",")
}

/**
 * Check naming compliance
 */
fun isNamingNormal(name: String): Boolean {
    val rule = "^[_a-zA-Z0-9]+$"
    return Regex(rule).matches(name)
}


/**
 * remove prefix or suffix
 */
val removeFun: (String, String, Boolean) -> String = { source, value, isPrefix ->
    if (isPrefix) source.removePrefix(value) else source.removeSuffix(value)
}

/**
 * add prefix or suffix
 */
val addFun: (String, String, Boolean) -> String = { source, value, isPrefix ->
    if (isPrefix) "${value}_$source" else "${source}_$value"
}

/**
 * naming by target
 */
val namingByTarget: (String, OptTarget) -> String = { source, target ->
    if (target == OptTarget.Table) toCamelCase(source, true) else toCamelCase(source)
}

/**
 * apply rule list
 */
val applyRule: (String, StrategyRule) -> String = { source, rule ->
    if (rule.position == OptPosition.Prefix.name) {
        if (rule.operator == Operator.Add.name)
            addFun(source, rule.optValue, true)
        else removeFun(source, rule.optValue, true)
    } else {
        if (rule.operator == Operator.Add.name)
            addFun(source, rule.optValue, false)
        else removeFun(source, rule.optValue, false)
    }
}

/**
 * Apply Rules to Rename table or column
 */
fun naming(name: String, rules: List<StrategyRule>, target: OptTarget = OptTarget.Table): String {
    var dstName = name
    rules
        .filter { it.optValue.isNotBlank() }
        .takeIf { it.isNotEmpty() }
        ?.let { it.forEach { rule -> run { dstName = applyRule(dstName, rule) } } }

    return namingByTarget(dstName, target)
}
