package dev.huyaro.gen.util

/**
 * @author yanghu
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
fun trimAndSplit(str: String): List<String> {
    val value = emptyList<String>()
    if (str.isEmpty() || str.isBlank()) {
        return value
    }
    val splitArr = Regex("\\s+").replace(str, " ").split(" ")
    if (splitArr.size == 1 && splitArr[0].isBlank()) {
        return value
    }
    return splitArr
}