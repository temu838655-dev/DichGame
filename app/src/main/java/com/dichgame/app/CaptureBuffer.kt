package com.dichgame.app

/** Owns the reader attached to a screen projection. Accessed on the service's main thread. */
internal class CaptureBuffer<T : AutoCloseable> : AutoCloseable {
    var reader: T? = null
        private set
    var width = 0
        private set
    var height = 0
        private set
    private var attached = false

    fun resize(width: Int, height: Int, create: () -> T, attach: (T) -> Unit): Boolean {
        if (attached && this.width == width && this.height == height) return false

        val next = create()
        // The display may already have resized when setting its surface fails.
        // Retry even if the device rotates back to the previous reader's size.
        attached = false
        try {
            attach(next)
            attached = true
        } finally {
            if (!attached) next.close()
        }

        val previous = reader
        reader = next
        this.width = width
        this.height = height
        previous?.close()
        return true
    }

    override fun close() {
        val previous = reader
        reader = null
        attached = false
        width = 0
        height = 0
        previous?.close()
    }
}
