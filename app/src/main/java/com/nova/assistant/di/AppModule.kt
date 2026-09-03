package com.nova.assistant.di

import android.content.Context
import androidx.room.Room
import com.nova.assistant.BuildConfig
import com.nova.assistant.data.local.MemoryDao
import com.nova.assistant.data.local.MemoryDatabase
import com.nova.assistant.data.remote.BackendApi
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            // TODO: add an auth interceptor here that attaches a per-device/session token —
            // never a raw LLM-provider API key — issued by your backend after sign-in.
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, moshi: Moshi): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.BACKEND_BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    @Provides
    @Singleton
    fun provideBackendApi(retrofit: Retrofit): BackendApi = retrofit.create(BackendApi::class.java)

    @Provides
    @Singleton
    fun provideMemoryDatabase(@ApplicationContext context: Context): MemoryDatabase =
        Room.databaseBuilder(context, MemoryDatabase::class.java, "nova_memory.db").build()

    @Provides
    @Singleton
    fun provideMemoryDao(db: MemoryDatabase): MemoryDao = db.memoryDao()
}
