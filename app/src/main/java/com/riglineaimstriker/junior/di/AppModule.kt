package com.riglineaimstriker.junior.di

import com.riglineaimstriker.junior.inference.InferenceEngine
import com.riglineaimstriker.junior.inference.LlamaCppEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    @Binds @Singleton
    abstract fun bindEngine(impl: LlamaCppEngine): InferenceEngine
}
