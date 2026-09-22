package dev.jacobandersen.bastion.graphql

sealed interface Mf2ValueGraphql

data class Mf2String(
    val value: String,
) : Mf2ValueGraphql

data class Mf2Boolean(
    val value: Boolean,
) : Mf2ValueGraphql

data class Mf2Number(
    val value: Double,
) : Mf2ValueGraphql

data class Mf2ObjectGraphql(
    val type: List<String>,
    val properties: List<Mf2PropertyGraphql>,
    val children: List<Mf2ObjectGraphql>,
) : Mf2ValueGraphql

data class Mf2PropertyGraphql(
    val name: String,
    val values: List<Mf2ValueGraphql>,
)

sealed interface Mf2JsonValueGraphql

data class Mf2JsonString(
    val value: String,
) : Mf2JsonValueGraphql

data class Mf2JsonNumber(
    val value: Double,
) : Mf2JsonValueGraphql

data class Mf2JsonBoolean(
    val value: Boolean,
) : Mf2JsonValueGraphql

data class Mf2JsonObject(
    val fields: List<Mf2JsonField>,
) : Mf2ValueGraphql,
    Mf2JsonValueGraphql

data class Mf2JsonField(
    val name: String,
    val value: Mf2JsonValueGraphql?,
)

data class Mf2JsonArray(
    val values: List<Mf2JsonValueGraphql?>,
) : Mf2ValueGraphql,
    Mf2JsonValueGraphql
