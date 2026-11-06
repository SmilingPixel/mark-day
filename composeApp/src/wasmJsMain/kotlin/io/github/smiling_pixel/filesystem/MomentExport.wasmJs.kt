package io.github.smiling_pixel.filesystem

import kotlinx.browser.document
import org.khronos.webgl.Int8Array
import org.khronos.webgl.set
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag
import kotlin.js.JsAny
import kotlin.js.toJsArray

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
internal actual suspend fun writeMomentExportFiles(files: List<MomentExportFile>): MomentExportResult =
    try {
        files.forEach { file ->
            val bytes = Int8Array(file.content.size)
            file.content.forEachIndexed { index, byte -> bytes[index] = byte }
            val blob = Blob(arrayOf<JsAny?>(bytes.buffer).toJsArray(), BlobPropertyBag(type = file.mimeType))
            val url = URL.createObjectURL(blob)
            val anchor = document.createElement("a") as HTMLAnchorElement
            anchor.href = url
            anchor.download = file.fileName
            document.body?.appendChild(anchor)
            anchor.click()
            anchor.remove()
            URL.revokeObjectURL(url)
        }
        MomentExportResult.Success(files.size, "browser downloads")
    } catch (e: Exception) {
        MomentExportResult.Failure("Moments could not be downloaded (${e::class.simpleName}).")
    }
