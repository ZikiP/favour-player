package com.ziki.favourplayer

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import okhttp3.OkHttpClient

class FavourApp : Application(), ImageLoaderFactory {

    private val coverClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", Resolver.DESKTOP_UA)
                        .build(),
                )
            }
            .build()
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient(coverClient)
            .crossfade(true)
            .build()
}
