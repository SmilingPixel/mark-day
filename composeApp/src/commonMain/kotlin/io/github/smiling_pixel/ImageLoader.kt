package io.github.smiling_pixel

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import io.github.smiling_pixel.filesystem.LocalFileFetcher
import io.github.smiling_pixel.filesystem.fileManager

fun getAsyncImageLoader(context: PlatformContext): ImageLoader {
    return ImageLoader.Builder(context)
        .components {
            add(LocalFileFetcher.Factory(fileManager)) // use localfile scheme, e.g., `localfile://myimage.jpg`, see LocalFileFetcher implementation for details
            add(KtorNetworkFetcherFactory())
        }
        .crossfade(true)
        .build()
}
