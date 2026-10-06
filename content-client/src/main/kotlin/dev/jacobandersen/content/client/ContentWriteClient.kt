package dev.jacobandersen.content.client

import java.io.InputStream

/**
 * Write access to the content service. Used by writers (Forge today, others
 * later) that hold a writer service token. The boundary is mf2: callers send
 * the canonical post document plus command hints; the content service owns
 * post-type discovery, slug allocation and timestamps.
 */
interface ContentWriteClient {
    /** Creates a post; returns its canonical identity. */
    fun create(command: CreatePostCommand): WritePostResult

    /** Updates a post by id; returns its canonical identity (slug may change). */
    fun update(
        id: String,
        command: UpdatePostCommand,
    ): WritePostResult

    /** Soft-deletes a post by id. */
    fun delete(id: String): WritePostResult

    /** Undeletes a post by id. */
    fun undelete(id: String): WritePostResult

    /** Uploads media (Micropub `/media`); returns the stored media URL. */
    fun uploadMedia(
        filename: String,
        contentType: String?,
        bytes: InputStream,
    ): MediaUploadResult
}
