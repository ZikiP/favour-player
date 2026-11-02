package com.ziki.favourplayer

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory

class FavourApp : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader = CoverCache.imageLoader(this)
}
