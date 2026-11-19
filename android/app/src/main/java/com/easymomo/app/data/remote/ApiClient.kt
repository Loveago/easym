package com.easymomo.app.data.remote

import com.easymomo.app.BuildConfig
import com.easymomo.app.data.local.SessionStore
import com.google.gson.Gson
import okhttp3.ConnectionPool
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private val sharedGson: Gson by lazy(LazyThreadSafetyMode.PUBLICATION) { Gson() }

    fun create(sessionStore: SessionStore): EasyMoMoApiService {
        val authInterceptor = Interceptor { chain ->
            val token = sessionStore.authToken
            val reqBuilder = chain.request().newBuilder()
                .header("X-Device-Id", sessionStore.deviceId)
                .header("X-Device-Name", sessionStore.deviceName)
            if (!token.isNullOrBlank()) {
                reqBuilder.header("Authorization", "Bearer $token")
            }
            chain.proceed(reqBuilder.build())
        }

        val clientBuilder = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .connectionPool(ConnectionPool(3, 3, TimeUnit.MINUTES))
            .retryOnConnectionFailure(true)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)

        // Avoid logging interceptor allocation/overhead entirely in release builds
        if (BuildConfig.DEBUG) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
                redactHeader("Authorization")
                redactHeader("Cookie")
            }
            clientBuilder.addInterceptor(logging)
        }

        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(clientBuilder.build())
            .addConverterFactory(GsonConverterFactory.create(sharedGson))
            .build()
            .create(EasyMoMoApiService::class.java)
    }
}
