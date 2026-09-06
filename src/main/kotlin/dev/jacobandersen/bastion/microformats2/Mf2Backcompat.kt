package dev.jacobandersen.bastion.microformats2

/**
 * Backward-compatibility data for the microformats2 parsing algorithm: classic
 * (microformats1 / hAtom / hCard style) root class names and their per-vocabulary
 * property class mappings, encoded from the vocabulary tables on the microformats
 * wiki. Only used when an element carries no microformats2 `h-*` root class.
 */
internal object Mf2Backcompat {

    /** Classic root class name -> microformats2 root type(s) it represents. */
    val roots: Map<String, List<String>> = mapOf(
        "vcard" to listOf("h-card"),
        "hentry" to listOf("h-entry"),
        "vevent" to listOf("h-event"),
        "adr" to listOf("h-adr"),
        "geo" to listOf("h-geo"),
        "hproduct" to listOf("h-product"),
        "hrecipe" to listOf("h-recipe"),
        "hresume" to listOf("h-resume"),
        "hreview" to listOf("h-review"),
        "hreview-aggregate" to listOf("h-review-aggregate"),
    )

    private data class Bc(val prefix: Char, val name: String)

    private val vcard: Map<String, Bc> = mapOf(
        "fn" to Bc('p', "name"),
        "honorific-prefix" to Bc('p', "honorific-prefix"),
        "given-name" to Bc('p', "given-name"),
        "additional-name" to Bc('p', "additional-name"),
        "family-name" to Bc('p', "family-name"),
        "sort-string" to Bc('p', "sort-string"),
        "nickname" to Bc('p', "nickname"),
        "email" to Bc('u', "email"),
        "logo" to Bc('u', "logo"),
        "photo" to Bc('u', "photo"),
        "url" to Bc('u', "url"),
        "uid" to Bc('u', "uid"),
        "category" to Bc('p', "category"),
        "adr" to Bc('p', "adr"),
        "post-office-box" to Bc('p', "post-office-box"),
        "extended-address" to Bc('p', "extended-address"),
        "street-address" to Bc('p', "street-address"),
        "locality" to Bc('p', "locality"),
        "region" to Bc('p', "region"),
        "postal-code" to Bc('p', "postal-code"),
        "country-name" to Bc('p', "country-name"),
        "label" to Bc('p', "label"),
        "geo" to Bc('p', "geo"),
        "latitude" to Bc('p', "latitude"),
        "longitude" to Bc('p', "longitude"),
        "tel" to Bc('p', "tel"),
        "note" to Bc('p', "note"),
        "bday" to Bc('d', "bday"),
        "key" to Bc('u', "key"),
        "org" to Bc('p', "org"),
        "organization-name" to Bc('p', "organization-name"),
        "organization-unit" to Bc('p', "organization-unit"),
        "title" to Bc('p', "job-title"),
        "role" to Bc('p', "role"),
        "impp" to Bc('u', "impp"),
        "tz" to Bc('p', "tz"),
        "rev" to Bc('d', "rev"),
    )

    private val hentry: Map<String, Bc> = mapOf(
        "entry-title" to Bc('p', "name"),
        "entry-summary" to Bc('p', "summary"),
        "entry-content" to Bc('e', "content"),
        "published" to Bc('d', "published"),
        "updated" to Bc('d', "updated"),
        "author" to Bc('p', "author"),
        "category" to Bc('p', "category"),
        "geo" to Bc('p', "geo"),
        "latitude" to Bc('p', "latitude"),
        "longitude" to Bc('p', "longitude"),
    )

    private val vevent: Map<String, Bc> = mapOf(
        "summary" to Bc('p', "name"),
        "dtstart" to Bc('d', "start"),
        "dtend" to Bc('d', "end"),
        "duration" to Bc('d', "duration"),
        "description" to Bc('p', "description"),
        "url" to Bc('u', "url"),
        "category" to Bc('p', "category"),
        "location" to Bc('p', "location"),
        "geo" to Bc('p', "geo"),
        "latitude" to Bc('p', "latitude"),
        "longitude" to Bc('p', "longitude"),
    )

    private val adr: Map<String, Bc> = mapOf(
        "post-office-box" to Bc('p', "post-office-box"),
        "extended-address" to Bc('p', "extended-address"),
        "street-address" to Bc('p', "street-address"),
        "locality" to Bc('p', "locality"),
        "region" to Bc('p', "region"),
        "postal-code" to Bc('p', "postal-code"),
        "country-name" to Bc('p', "country-name"),
        "label" to Bc('p', "label"),
    )

    private val geo: Map<String, Bc> = mapOf(
        "latitude" to Bc('p', "latitude"),
        "longitude" to Bc('p', "longitude"),
    )

    private val hproduct: Map<String, Bc> = mapOf(
        "fn" to Bc('p', "name"),
        "photo" to Bc('u', "photo"),
        "brand" to Bc('p', "brand"),
        "category" to Bc('p', "category"),
        "description" to Bc('p', "description"),
        "identifier" to Bc('u', "identifier"),
        "url" to Bc('u', "url"),
        "review" to Bc('p', "review"),
        "price" to Bc('p', "price"),
    )

    private val hrecipe: Map<String, Bc> = mapOf(
        "fn" to Bc('p', "name"),
        "ingredient" to Bc('p', "ingredient"),
        "yield" to Bc('p', "yield"),
        "instructions" to Bc('e', "instructions"),
        "duration" to Bc('d', "duration"),
        "photo" to Bc('u', "photo"),
        "summary" to Bc('p', "summary"),
        "author" to Bc('p', "author"),
        "published" to Bc('d', "published"),
        "nutrition" to Bc('p', "nutrition"),
    )

    private val hresume: Map<String, Bc> = mapOf(
        "summary" to Bc('p', "summary"),
        "contact" to Bc('p', "contact"),
        "education" to Bc('p', "education"),
        "experience" to Bc('p', "experience"),
        "skill" to Bc('p', "skill"),
        "affiliation" to Bc('p', "affiliation"),
    )

    private val hreview: Map<String, Bc> = mapOf(
        "summary" to Bc('p', "name"),
        "reviewer" to Bc('p', "author"),
        "dtreviewed" to Bc('d', "published"),
        "rating" to Bc('p', "rating"),
        "best" to Bc('p', "best"),
        "worst" to Bc('p', "worst"),
        "description" to Bc('e', "content"),
    )

    private val hreviewAggregate: Map<String, Bc> = mapOf(
        "summary" to Bc('p', "name"),
        "rating" to Bc('p', "rating"),
        "best" to Bc('p', "best"),
        "worst" to Bc('p', "worst"),
        "count" to Bc('p', "count"),
        "votes" to Bc('p', "votes"),
    )

    private val propertiesByRoot: Map<String, Map<String, Bc>> = mapOf(
        "vcard" to vcard,
        "hentry" to hentry,
        "vevent" to vevent,
        "adr" to adr,
        "geo" to geo,
        "hproduct" to hproduct,
        "hrecipe" to hrecipe,
        "hresume" to hresume,
        "hreview" to hreview,
        "hreview-aggregate" to hreviewAggregate,
    )

    fun isClassicRoot(className: String): Boolean = roots.containsKey(className)

    fun classicRootTypes(classNames: Set<String>): List<String> {
        return classNames.asSequence()
            .mapNotNull { roots[it] }
            .flatten()
            .distinct()
            .sorted()
            .toList()
    }

    /**
     * Classic property class names in scope for the given classic root classes,
     * mapped to their microformats2 parsing prefix and property name.
     */
    fun classicPropertyClasses(classicRoots: Set<String>): Map<String, ParsedPropertyClass> {
        val out = mutableMapOf<String, ParsedPropertyClass>()
        for (root in classicRoots) {
            propertiesByRoot[root]?.forEach { (classicName, bc) ->
                out[classicName] = ParsedPropertyClass(bc.prefix, bc.name)
            }
        }
        return out
    }
}

/** A parsed property class name: the prefix and the resulting property name. */
internal data class ParsedPropertyClass(val prefix: Char, val name: String)
