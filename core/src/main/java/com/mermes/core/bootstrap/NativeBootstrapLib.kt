package com.mermes.core.bootstrap

/**
 * JNI Native methods for bootstrap zip access
 */
internal object NativeBootstrapLib {

    /**
     * Load native library
     */
    fun load() {
        System.loadLibrary("mermes-bootstrap")
    }

    /**
     * Get the embedded bootstrap zip byte array
     *
     * @return Zip file byte array
     */
    external fun getZip(): ByteArray

    init {
        load()
    }
}
