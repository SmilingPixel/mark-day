package io.github.smiling_pixel

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform