package com.feilema.app.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.feilema.app.data.local.entity.BehaviorEventEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 真机 instrumentation 测试：用内存 Room 验证 DAO 的区间统计与排序语义。
 * 这些语义是统计口径的地基，单元测试（JVM）无法覆盖 SQLite 行为。
 */
@RunWith(AndroidJUnit4::class)
class BehaviorEventDaoTest {

    private lateinit var db: FeiLeMaDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FeiLeMaDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun event(occurredAt: Long, note: String? = null) = BehaviorEventEntity(
        occurredAt = occurredAt,
        createdAt = occurredAt,
        note = note,
    )

    @Test
    fun countBetween_usesHalfOpenInterval() = runBlocking {
        val dao = db.behaviorEventDao()
        val start = 1_000_000L
        val end = start + 86_400_000L

        dao.insert(event(start - 1))  // 左边界外
        dao.insert(event(start))      // 左闭：算
        dao.insert(event(start + 1))  // 算
        dao.insert(event(end - 1))    // 算
        dao.insert(event(end))        // 右开：不算

        assertEquals(3, dao.countBetween(start, end))
        assertEquals(4, dao.countBetween(start, end + 1))
        assertEquals(4, dao.countBetween(start - 1, end))
        assertEquals(5, dao.countBetween(0, Long.MAX_VALUE))
    }

    @Test
    fun observeRecent_ordersByOccurredAtDescThenIdDesc() = runBlocking {
        val dao = db.behaviorEventDao()
        val older = dao.insert(event(1_000))
        val newer = dao.insert(event(3_000))
        val sameTimeLaterInsert = dao.insert(event(3_000))

        assertEquals(
            listOf(sameTimeLaterInsert, newer),
            dao.observeRecent(2).first().map { it.id },
        )
        assertEquals(3, dao.observeAll().first().size)
        assertEquals(older, dao.observeAll().first().last().id)
    }

    @Test
    fun updateAndDelete_changeAggregates() = runBlocking {
        val dao = db.behaviorEventDao()
        val id = dao.insert(event(5_000, note = "旧"))
        dao.update(id, 9_000, "新")

        val updated = dao.observeAll().first().single()
        assertEquals(9_000L, updated.occurredAt)
        assertEquals("新", updated.note)
        assertEquals(1, dao.countBetween(9_000, 9_001))

        dao.deleteById(id)
        assertEquals(0, dao.observeAll().first().size)
        assertEquals(0, dao.countBetween(0, Long.MAX_VALUE))
    }
}
