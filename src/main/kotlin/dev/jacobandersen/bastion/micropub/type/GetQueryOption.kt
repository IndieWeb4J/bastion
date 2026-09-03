package dev.jacobandersen.bastion.micropub.type

enum class GetQueryOption(val param: String?) {
    CONFIG(null),
    SOURCE(null),
    SYNDICATE_TO("syndicate-to");

    companion object {
        fun fromString(value: String?): GetQueryOption? {
            if (value.isNullOrBlank()) return null

            return entries.find {
                return@find if (it.param == null) {
                    it.name.equals(value, true)
                } else {
                    it.param.equals(value, true)
                }
            }
        }
    }
}