package com.feilema.app.feature.widget

import com.feilema.app.domain.repository.BehaviorRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock

/**
 * 桌面小组件运行在没有 Activity、也不走 Hilt 注入的进程里，
 * 用 EntryPoint 拿依赖。
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun behaviorRepository(): BehaviorRepository
    fun clock(): Clock
}
