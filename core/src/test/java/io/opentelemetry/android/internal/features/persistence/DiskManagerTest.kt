/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.internal.features.persistence

import io.mockk.Called
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.clearMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.verify
import io.opentelemetry.android.features.diskbuffering.DiskBufferingConfig
import io.opentelemetry.android.internal.services.CacheStorage
import io.opentelemetry.android.internal.services.Preferences
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

internal class DiskManagerTest {
    @MockK
    lateinit var cacheStorage: CacheStorage

    @MockK
    lateinit var preferences: Preferences

    @MockK
    lateinit var diskBufferingConfig: DiskBufferingConfig

    @TempDir
    lateinit var cacheDir: File
    private lateinit var diskManager: DiskManager

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        every { cacheStorage.cacheDir }.returns(cacheDir)
        diskManager =
            DiskManager(cacheStorage, preferences, diskBufferingConfig)
    }

    @Test
    fun `provides the signal buffer dir`() {
        val expected = File(cacheDir, "opentelemetry/signals")
        assertThat(diskManager.signalsBufferDir).isEqualTo(expected)
        assertThat(expected.exists()).isTrue()
    }

    @Test
    fun `provides a temp dir`() {
        every { cacheStorage.cacheDir }.returns(cacheDir)
        val expected = File(cacheDir, "opentelemetry/temp")
        assertThat(diskManager.temporaryDir).isEqualTo(expected)
        assertThat(expected.exists()).isTrue()
    }

    @Test
    fun `cleans up the temp dir before providing it`() {
        val dir = File(cacheDir, "opentelemetry/temp")
        assertTrue(dir.mkdirs())
        assertTrue(File(dir, "somefile.tmp").createNewFile())
        assertTrue(File(dir, "some_other_file.tmp").createNewFile())
        assertTrue(File(dir, "somedir").mkdirs())
        assertTrue(File(dir, "somedir/some_other_file.tmp").createNewFile())
        val temporaryDir = diskManager.temporaryDir
        assertThat(temporaryDir.exists()).isTrue()
        assertThat(temporaryDir.listFiles()?.size ?: -1).isZero()
    }

    @Test
    fun `can get the max cache file size`() {
        val persistenceSize = 1024 * 1024 * 2
        every { diskBufferingConfig.maxCacheFileSize }.returns(persistenceSize)
        assertThat(diskManager.maxCacheFileSize).isEqualTo(persistenceSize)
        verify {
            diskBufferingConfig.maxCacheFileSize
        }
    }

    @Test
    fun `can get max signal folder size`() {
        val maxCacheSize = (10 * 1024 * 1024).toLong() // 10 MB
        val maxCacheFileSize = 1024 * 1024 // 1 MB
        every { diskBufferingConfig.maxCacheSize }.returns(maxCacheSize.toInt())
        every { diskBufferingConfig.maxCacheFileSize }.returns(maxCacheFileSize)
        every { preferences.retrieveInt(MAX_FOLDER_SIZE_KEY, -1) }.returns(-1)
        every { preferences.store(any(), any()) } just Runs

        // Expects the size of a single signal type folder, to use as temporary space for reading.
        val expected = (maxCacheSize / 3).toInt()
        assertThat(diskManager.maxFolderSize).isEqualTo(expected)
        verify {
            preferences.store(MAX_FOLDER_SIZE_KEY, expected)
        }

        // On a second call, should get the value from the preferences.
        clearMocks(cacheStorage, diskBufferingConfig, preferences)
        every { preferences.retrieveInt(MAX_FOLDER_SIZE_KEY, -1) }.returns(expected)
        assertThat(diskManager.maxFolderSize).isEqualTo(expected)

        verify {
            preferences.retrieveInt(MAX_FOLDER_SIZE_KEY, -1)
        }
        confirmVerified(preferences)
        verify { cacheStorage wasNot Called }
        verify { diskBufferingConfig wasNot Called }
    }

    companion object {
        private const val MAX_FOLDER_SIZE_KEY = "max_signal_folder_size"
    }
}
