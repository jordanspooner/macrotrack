package com.macrotrack.di

import com.macrotrack.data.repository.FoodRepository
import com.macrotrack.data.repository.FoodRepositoryImpl
import com.macrotrack.data.repository.LogRepository
import com.macrotrack.data.repository.LogRepositoryImpl
import com.macrotrack.data.repository.SectionRepository
import com.macrotrack.data.repository.SectionRepositoryImpl
import com.macrotrack.data.repository.SettingsRepository
import com.macrotrack.data.repository.SettingsRepositoryImpl
import com.macrotrack.data.repository.UserFoodRepository
import com.macrotrack.data.repository.UserFoodRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindFoodRepository(
        impl: FoodRepositoryImpl
    ): FoodRepository

    @Binds
    abstract fun bindSectionRepository(
        impl: SectionRepositoryImpl
    ): SectionRepository

    @Binds
    abstract fun bindLogRepository(
        impl: LogRepositoryImpl
    ): LogRepository

    @Binds
    abstract fun bindSettingsRepository(
        impl: SettingsRepositoryImpl
    ): SettingsRepository

    @Binds
    abstract fun bindUserFoodRepository(
        impl: UserFoodRepositoryImpl
    ): UserFoodRepository
}
