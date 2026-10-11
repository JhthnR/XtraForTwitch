package com.github.andreyasadchy.xtra.db

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.github.andreyasadchy.xtra.model.VideoQuality
import com.github.andreyasadchy.xtra.model.ui.OfflineVideo
import com.github.andreyasadchy.xtra.ui.player.selectDownloadQuality
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(application = Application::class, sdk = [23, 28])
class FeaturePortsMigrationTest {
    @Test fun `35 to 36 retains old rows and persists exact download identity across reopen`() {
        val context = RuntimeEnvironment.getApplication()
        val name = "quality-migration-test.db"
        context.deleteDatabase(name)
        val relative = "schemas/com.github.andreyasadchy.xtra.db.AppDatabase/35.json"
        val schema = JSONObject(listOf(File(relative), File("app/$relative")).first { it.isFile }.readText()).getJSONObject("database")
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices")
                for (i in 0 until (indices?.length() ?: 0)) db.execSQL(indices!!.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (index in 0 until setup.length()) db.execSQL(setup.getString(index))
            db.execSQL("INSERT INTO videos (id, name, quality, progress, max_progress, bytes, status, downloadChat, downloadChatEmotes, chatProgress, maxChatProgress, chatBytes, chatOffsetSeconds, playlistToFile, live) VALUES (1, 'Legacy recording', '720p60', 0, 100, 0, 0, 0, 0, 0, 100, 0, 0, 0, 1)")
            db.execSQL("INSERT INTO shown_notifications (channelId, startedAt) VALUES ('legacy-channel', 123456)")
            db.version = 35
        }
        fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(FEATURE_PORTS_MIGRATION).allowMainThreadQueries().build()
        val original = open()
        val notification = original.shownNotificationsDao().getAll().single()
        assertEquals("legacy-channel", notification.channelId)
        assertEquals(123456L, notification.startedAt)
        val legacy = original.videos().getById(1)!!
        assertEquals("Legacy recording", legacy.name)
        assertEquals("720p60", legacy.quality)
        assertNull(legacy.qualityCodecs)
        assertNull(legacy.qualityBitrate)
        val id = original.videos().insert(OfflineVideo(
            quality = "1080p60", qualityCodecs = "avc1.64002a", qualityBitrate = 3000000, live = true,
        )).toInt()
        original.close()

        val reopened = open()
        val saved = reopened.videos().getById(id)!!
        val expected = VideoQuality("1080p60", "avc1.64002a", "https://example.com/selected/index.m3u8?token=fresh", 3000000)
        val other = expected.copy(url = "https://example.com/other/index.m3u8?token=fresh", bitrate = 4000000)
        val selection = selectDownloadQuality(listOf(other, expected), VideoQuality(saved.quality, saved.qualityCodecs, bitrate = saved.qualityBitrate))
        assertSame(expected, selection)
        reopened.close()
        context.deleteDatabase(name)
    }
}
