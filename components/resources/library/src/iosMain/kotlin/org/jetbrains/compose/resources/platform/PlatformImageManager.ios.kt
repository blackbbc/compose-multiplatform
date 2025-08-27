/*
 * Tencent is pleased to support the open source community by making tvCompose available.
 * Copyright (C) 2025 THL A29 Limited, a Tencent company. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * region Tencent Code
 */
package org.jetbrains.compose.resources.platform

import androidx.compose.ui.graphics.UikitImageBitmap
import androidx.compose.runtime.CurrentPlatform
import androidx.compose.runtime.PlatformType
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.v2.nativefoundation.decodeUIImageFromPath
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.compose.resources.DensityQualifier
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.LanguageQualifier
import org.jetbrains.compose.resources.RegionQualifier
import org.jetbrains.compose.resources.ResourceEnvironment
import org.jetbrains.compose.resources.ThemeQualifier
import org.jetbrains.compose.resources.getResourceItemByEnvironment
import org.jetbrains.compose.resources.readResourceBytes
import org.jetbrains.compose.resources.toImageBitmap


@OptIn(InternalResourceApi::class, ExperimentalResourceApi::class)
internal actual object PlatformImageManagerImpl : PlatformImageManager {
    private val emptyImageBitmap: ImageBitmap by lazy { ImageBitmap(1, 1) }
    private val imageCacheMutex = Mutex()
    private val imageCache = mutableMapOf<String, Deferred<UikitImageCache>>()

    private val environment = ResourceEnvironment(
        language = LanguageQualifier("en"),
        region = RegionQualifier("US"),
        theme = ThemeQualifier.LIGHT,
        density = DensityQualifier.XHDPI
    )

    override val isUsePlatformImage: Boolean
        get() = CurrentPlatform == PlatformType.IOS

    /**
     * 提供了异步加载Painter的能力，同时兼容SKIA渲染和CALayer渲染。
     */
    suspend fun loadImagePainter(
        resource: DrawableResource,
        drawInSkia: Boolean = false
    ): Painter {
        val path = resource.getResourceItemByEnvironment(environment).path
        val imageBitmap = if (isUsePlatformImage && !drawInSkia) {
            loadImageBitmap(path)
        } else {
            val cached = loadImage(path) {
                UikitImageCache.Bitmap(it.toImageBitmap())
            } as UikitImageCache.Bitmap
            cached.bitmap
        }
        return BitmapPainter(imageBitmap)
    }

    override suspend fun loadImageBitmap(path: String): ImageBitmap {
        val cached = loadNativeImage(path) {
            UikitImageCache.Bitmap(
                decodeUIImageFromPath(path)?.let(::UikitImageBitmap) ?: emptyImageBitmap
            )
        } as UikitImageCache.Bitmap
        return cached.bitmap
    }

    private suspend fun loadNativeImage(
        path: String,
        decode: (String) -> UikitImageCache
    ): UikitImageCache = coroutineScope {
        val deferred = imageCacheMutex.withLock {
            imageCache.getOrPut(path) {
                // LAZY - to free the mutex lock as fast as possible
                async(start = CoroutineStart.LAZY) {
                    decode(path)
                }
            }
        }
        deferred.await()
    }

    private suspend fun loadImage(
        path: String,
        decode: (ByteArray) -> UikitImageCache
    ): UikitImageCache = coroutineScope {
        val deferred = imageCacheMutex.withLock {
            imageCache.getOrPut(path) {
                // LAZY - to free the mutex lock as fast as possible
                async(start = CoroutineStart.LAZY) {
                    decode(readResourceBytes(path))
                }
            }
        }
        deferred.await()
    }

    fun clearCache() {
        imageCache.clear()
    }
}