package com.ziki.favourplayer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 干净模式的优先级：默认跟服务端，用户手动改过之后以本机为准。
 */
class CleanModeTest {

    @Before
    fun reset() {
        CleanMode.reset()
    }

    @Test
    fun followsServerByDefault() {
        CleanMode.setRemote(true)
        assertTrue(CleanMode.enabled)
        CleanMode.setRemote(false)
        assertFalse(CleanMode.enabled)
    }

    @Test
    fun localOverrideWins() {
        CleanMode.setRemote(true)
        // 服务端开着，用户在本机关掉：本地说了算
        CleanMode.setLocal(false)
        assertFalse(CleanMode.enabled)
        // 之后同步也不会被服务端改回去
        CleanMode.setRemote(false)
        assertFalse(CleanMode.enabled)
    }

    @Test
    fun localOverrideCanEnableCleanMode() {
        CleanMode.setRemote(false)
        CleanMode.setLocal(true)
        assertTrue(CleanMode.enabled)
        CleanMode.setRemote(false)
        assertTrue(CleanMode.enabled)
    }
}
