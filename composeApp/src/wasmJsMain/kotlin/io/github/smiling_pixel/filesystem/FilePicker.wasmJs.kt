package io.github.smiling_pixel.filesystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.browser.document
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.get
import org.w3c.dom.HTMLInputElement
import org.w3c.files.File
import org.w3c.files.FileReader
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

actual class PlatformFile(val file: File)

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
actual suspend fun PlatformFile.readBytes(): ByteArray = suspendCoroutine { cont ->
    val reader = FileReader()
    reader.onload = {
        val buffer = reader.result as ArrayBuffer
        val int8Array = Int8Array(buffer)
        val bytes = ByteArray(int8Array.length)
        for (i in 0 until int8Array.length) {
            bytes[i] = int8Array[i]
        }
        cont.resume(bytes)
    }
    reader.onerror = {
        cont.resume(byteArrayOf())
    }
    reader.readAsArrayBuffer(file)
}

actual fun PlatformFile.name(): String = file.name

@Composable
actual fun rememberFilePicker(onFilesSelected: (List<PlatformFile>) -> Unit): FilePickerLauncher {
    return remember {
        object : FilePickerLauncher {
            override fun launch() {
                val input = document.createElement("input") as HTMLInputElement
                input.type = "file"
                input.multiple = true
                input.style.display = "none"
                input.onchange = {
                    val filesList = input.files
                    if (filesList != null) {
                        val files = (0 until filesList.length).map {
                            PlatformFile(filesList.item(it)!!)
                        }
                        onFilesSelected(files)
                    }
                }
                document.body?.appendChild(input)
                input.click()
                document.body?.removeChild(input)
            }
        }
    }
}
