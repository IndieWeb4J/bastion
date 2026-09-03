package dev.jacobandersen.bastion.micropub.util

import org.springframework.util.MultiValueMap

object MicropubParamNormalizer {
    fun normalizeDuplicates(map: MultiValueMap<String, String>): MutableMap<String, Array<String>> {
        return normalizeDuplicates(map.mapValues { it.value.toTypedArray() })
    }

    fun normalizeDuplicates(map: Map<String, Array<String>>): MutableMap<String, Array<String>> {
        val normalizedData = HashMap<String, Array<String>>()

        map.forEach { (key, values) ->
            var key = key
            if (key.endsWith("[]")) {
                key = key.removeSuffix("[]")
            }

            normalizedData.compute(key) { _, v ->
                return@compute if (v == null) {
                    values
                } else {
                    v + values
                }
            }
        }

        return normalizedData
    }
}