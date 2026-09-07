package dev.jacobandersen.bastion.micropub.syndication

/**
 * The syndication lifecycle actions a target may support. A target only ever
 * receives posts for actions it explicitly declares; Bastion sends downstream
 * syndication for the [CREATE] and [DELETE] actions.
 */
enum class SyndicationAction {
    CREATE,
    DELETE,
    UPDATE,
    UPLOAD,
}
