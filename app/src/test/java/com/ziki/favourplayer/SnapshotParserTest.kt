package com.ziki.favourplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotParserTest {

    @Test
    fun parsesExtendedSnapshot() {
        val raw = """
            {
              "updatedAt": 123,
              "items": [
                {
                  "url": "https://example.com/a",
                  "title": "示例作品",
                  "tags": [],
                  "favorite": true,
                  "watched": true,
                  "lastOpenedAt": 456,
                  "source": { "name": "Example" },
                  "cover": "",
                  "extras": [
                    { "key": "actress", "value": "演员甲 (别名), 演员乙" },
                    { "key": "genres", "value": "剧情,悬疑" },
                    { "key": "series", "value": "测试系列" }
                  ],
                  "createdAt": 1
                }
              ],
              "actressAvatars": { "演员甲": "https://example.com/avatar.jpg" },
              "actressMeta": { "演员甲": { "favorite": true, "order": 5 } }
            }
        """.trimIndent()

        val snapshot = SnapshotParser.parse(raw)
        val item = snapshot.items.single()

        assertEquals(listOf("演员甲", "演员乙"), item.actressNames())
        assertEquals(listOf("剧情", "悬疑"), item.genres())
        assertEquals(listOf("剧情", "悬疑"), item.displayLabels())
        assertEquals("测试系列", item.series())
        assertTrue(item.watched)
        assertEquals(456L, item.lastOpenedAt)
        assertEquals("Example", item.sourceName)
        assertEquals("https://example.com/avatar.jpg", snapshot.actressAvatars["演员甲"])
        assertEquals(true, snapshot.actressMeta["演员甲"]?.favorite)
        assertEquals(5, snapshot.actressMeta["演员甲"]?.order)
    }
}
