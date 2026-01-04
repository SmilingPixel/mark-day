package io.github.smiling_pixel

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import io.github.smiling_pixel.filesystem.LocalFileFetcher

fun getAsyncImageLoader(context: PlatformContext): ImageLoader {
    return ImageLoader.Builder(context)
        .components {
            add(LocalFileFetcher.Factory()) // use localfile schema, e.g., `localfile://myimage.jpg`
            add(KtorNetworkFetcherFactory())
        }
        .crossfade(true)
        .build()
}
