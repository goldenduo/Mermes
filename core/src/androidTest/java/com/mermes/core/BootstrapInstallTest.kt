package com.mermes.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mermes.core.bootstrap.MermesBootstrap
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BootstrapInstallTest {

    private val context by lazy {
        InstrumentationRegistry.getInstrumentation().targetContext
    }

    @Before
    fun setup() {
        MermesBootstrap.clearBootstrap(context)
    }

    @Test
    fun testInstallBootstrap() = runBlocking {
        val result = MermesBootstrap.installBootstrap(context)

        assertTrue("Install should succeed: ${result.error}", result.success)
        assertTrue("isBootstrapInstalled should be true", MermesBootstrap.isBootstrapInstalled(context))
        assertTrue("extractedFiles should be > 0", result.extractedFiles > 0)

        val bashFile = File(MermesBootstrap.getPrefixDir(context), "bin/bash")
        assertTrue("bash should exist", bashFile.exists())
        assertTrue("bash should be executable", bashFile.canExecute())
    }

    @Test
    fun testIdempotentInstall() = runBlocking {
        val first = MermesBootstrap.installBootstrap(context)
        assertTrue("First install should succeed", first.success)

        val second = MermesBootstrap.installBootstrap(context)
        assertTrue("Second install should succeed (skipped)", second.success)
        assertEquals("Second install should extract 0 files", 0, second.extractedFiles)
    }

    @Test
    fun testClearAndReinstall() = runBlocking {
        val first = MermesBootstrap.installBootstrap(context)
        assertTrue("First install should succeed", first.success)

        MermesBootstrap.clearBootstrap(context)
        assertFalse("Should not be installed after clear", MermesBootstrap.isBootstrapInstalled(context))

        val second = MermesBootstrap.installBootstrap(context)
        assertTrue("Reinstall should succeed", second.success)
        assertTrue("Should be installed after reinstall", MermesBootstrap.isBootstrapInstalled(context))
    }
}
