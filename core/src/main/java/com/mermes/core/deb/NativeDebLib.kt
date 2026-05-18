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
     * Get the number of embedded deb packages
     *
     * @return Number of deb packages
     */
    external fun getDebCount(): Int

    /**
     * Get deb package data by index
     *
     * @param index Package index
     * @return Deb file byte array
     */
    external fun getDebByIndex(index: Int): ByteArray

    /**
     * Get deb package data by architecture and name
     *
     * @param arch Architecture name (aarch64, arm, i686, x86_64)
     * @param packageName Package name
     * @return Deb file byte array, or null if not found
     */
    external fun getDebByArchAndName(arch: String, packageName: String): ByteArray?

    /**
     * Get all embedded deb package names
     *
     * @return Array of package names
     */
    external fun getDebNames(): Array<String>

    init {
        load()
    }
}
