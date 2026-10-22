package com.networkbroadcast.hospitality.tv

import android.content.Context
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import com.networkbroadcast.hospitality.brand.imageModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Precalienta Coil con límite de concurrencia; el ImageLoader conserva las respuestas en disco. */
internal suspend fun preloadImages(context: Context, urls: Iterable<String?>, maxConcurrent: Int = 5) {
    val uniqueUrls = urls.filterNotNull().map(String::trim).filter(String::isNotEmpty).distinct()
    if (uniqueUrls.isEmpty()) return
    val loader = SingletonImageLoader.get(context)
    val slots = Semaphore(maxConcurrent)
    coroutineScope {
        uniqueUrls.map { url ->
            async {
                slots.withPermit {
                    runCatching { loader.execute(ImageRequest.Builder(context).data(imageModel(url)).build()) }
                }
            }
        }.awaitAll()
    }
}
