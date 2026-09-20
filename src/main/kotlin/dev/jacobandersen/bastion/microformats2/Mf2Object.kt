package dev.jacobandersen.bastion.microformats2

/**
 * A parsed microformat: its `h-*` type(s), its properties in document order
 * and its bare children (nested microformats that are not property values).
 *
 * Instances are immutable; property updates are expressed with the
 * [setProperty], [addProperty] and [deleteProperty] helpers, which return new
 * instances.
 */
data class Mf2Object(
    val type: List<String>,
    val properties: Map<String, List<Mf2Value>> = emptyMap(),
    val children: List<Mf2Object>? = null,
) {
    init {
        require(type.isNotEmpty()) { "Mf2Object type must not be empty" }
        require(type.all { it.isNotBlank() }) { "Mf2Object type entries must not be blank" }
    }

    fun primaryType(): String = type.first()

    fun getProperty(key: String): List<Mf2Value> = properties[key].orEmpty()

    fun getFirstProperty(key: String): Mf2Value? = getProperty(key).firstOrNull()

    operator fun get(key: String): List<Mf2Value> = getProperty(key)

    /** Whether the property exists with at least one value. */
    fun hasProperty(key: String): Boolean = getProperty(key).isNotEmpty()

    fun setProperty(
        key: String,
        value: Mf2Value,
    ): Mf2Object = setProperty(key, listOf(value))

    fun setProperty(
        key: String,
        values: List<Mf2Value>,
    ): Mf2Object = copy(properties = properties + (key to values))

    fun addProperty(
        key: String,
        value: Mf2Value,
    ): Mf2Object = addProperty(key, listOf(value))

    fun addProperty(
        key: String,
        values: List<Mf2Value>,
    ): Mf2Object = copy(properties = properties + (key to (getProperty(key) + values)))

    fun deleteProperty(key: String): Mf2Object = copy(properties = properties - key)
}

/** The first non-blank plain-text value of the property, or null. */
fun Mf2Object.firstText(key: String): String? =
    getProperty(key).firstNotNullOfOrNull { it.plainTextOrNull }?.takeIf { it.isNotBlank() }

/** The first non-blank `html` value of the property, or null. */
fun Mf2Object.firstHtml(key: String): String? =
    getProperty(key).firstNotNullOfOrNull { it.htmlOrNull }?.takeIf { it.isNotBlank() }

/** All non-blank plain-text values of the property. */
fun Mf2Object.texts(key: String): List<String> =
    getProperty(key).mapNotNull { it.plainTextOrNull }.filter { it.isNotBlank() }
