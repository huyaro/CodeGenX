package dev.huyaro.gen.util

import dev.huyaro.gen.model.Operator
import dev.huyaro.gen.model.OptPosition
import dev.huyaro.gen.model.OptTarget
import dev.huyaro.gen.model.StrategyRule

/**
 * @author huyaro
 * @date 2022-11-21
 * @description Function details...
 */
/**
 * underline to camelcase
 */
fun camelCase(str: String, firstUpper: Boolean = false, delimiter: String = "_"): String {
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
 * trim all space and split
 */
fun trimAndSplit(value: String): List<String> {
    val valueList = emptyList<String>()
    if (value.isEmpty() || value.isBlank()) {
        return valueList
    }
    val splitArray = Regex("\\s+").replace(value, " ").split(",")
    if (splitArray.size == 1 && splitArray[0].isBlank()) {
        return valueList
    }
    return splitArray
}

/**
 * Check naming compliance
 */
fun isNamingNormal(name: String): Boolean {
    val rule = "^[_a-zA-Z0-9]+$"
    return Regex(rule).matches(name)
}

/**
 * Apply Rules Rename
 */
fun naming(name: String, rules: List<StrategyRule>, target: OptTarget = OptTarget.Table): String {

    val removeFun: (String, String, Boolean) -> String = { source: String, value: String, isPrefix: Boolean ->
        if (isPrefix) source.removePrefix(value) else source.removeSuffix(value)
    }
    val addFun: (String, String, Boolean) -> String = { source: String, value: String, isPrefix: Boolean ->
        if (isPrefix) "${value}_$source" else "${source}_$value"
    }
    val namingByTarget: (String) -> String = { source: String ->
        if (target == OptTarget.Table) camelCase(source, true) else camelCase(source)
    }

    val filterRules = rules.filter { it.optValue.isNotBlank() && (it.target == target.name) }
    if (filterRules.isEmpty()) {
        return namingByTarget(name)
    }

    var tabName = name
    filterRules
        .map { rule ->
            tabName = if (rule.position == OptPosition.Prefix.name) {
                if (rule.operator == Operator.Add.name)
                    addFun(tabName, rule.optValue, true)
                else removeFun(tabName, rule.optValue, true)
            } else {
                if (rule.operator == Operator.Add.name)
                    addFun(tabName, rule.optValue, false)
                else removeFun(tabName, rule.optValue, false)
            }
        }
    return namingByTarget(tabName)
}