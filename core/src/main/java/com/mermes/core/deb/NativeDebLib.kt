package com.mermes.core.deb

/**
 * JNI Native methods for deb package access
 */
internal object NativeDebLib {

    /**
     * Load native library
     */
    fun load() {
        System.loadLibrary("mermes-deb")
    }

    /**
     * Get the embedded deb packages zip as a byte array
     *
     * @return Zip file byte array
     */
    external fun getZip(): ByteArray

    init {
        load()
    }
}
