/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.example.background

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.background.workers.BlurWorker
import com.example.background.workers.CleanupWorker
import com.example.background.workers.SaveImageToFileWorker

// 此视图模型用于存储显示 BlurActivity 所需的所有数据，也将是您使用 WorkManager 启动后台工作的类。
class BlurViewModel(application: Application) : ViewModel() {

    internal var imageUri: Uri? = null
    internal var outputUri: Uri? = null

    private val workerManager = WorkManager.getInstance(application)

    init {
        imageUri = getImageUri(application.applicationContext)
    }

    /**
     * Create the WorkRequest to apply the blur and save the resulting image
     * @param blurLevel The amount to blur the image
     */
    internal fun applyBlur(blurLevel: Int) {
        // 创建 WorkRequest 链
        // 添加 WorkRequest 清理临时文件
        var continuation = workerManager.beginWith(
            OneTimeWorkRequest.from(CleanupWorker::class.java)
        )

        // 添加 WorkRequest 模糊图片
        // 添加对图片进行不同程度的模糊处理的功能
        for (i in 0 until blurLevel) {
            val blurBuilder = OneTimeWorkRequestBuilder<BlurWorker>()

            // Input the Uri if this is the first blur operation
            // After the first blur operation the input will be the output of previous blur operations.
            // 只有第一个 WorkRequest 需要且应该获取 URI 输入
            if (i == 0) {
                blurBuilder.setInputData(createInputDataForUri())
            }
            continuation = continuation.then(blurBuilder.build()) // 通过调用 then() 方法向此工作请求链中添加请求对象
        }

        // 添加 WorkRequest 保存图片
        val save = OneTimeWorkRequest.Builder(SaveImageToFileWorker::class.java).build()

        continuation = continuation.then(save)

        // Actually start the work
        continuation.enqueue()
    }

    /**
     * Creates the input data bundle which includes the Uri to operate on
     * @return Data which contains the Image Uri as a String
     * 输入和输出通过 Data 对象传入和传出。Data 对象是轻量化的键值对容器。
     */
    private fun createInputDataForUri(): Data {
        val builder = Data.Builder()
        imageUri?.let {
            builder.putString(KEY_IMAGE_URI, imageUri.toString())
        }
        return builder.build()
    }

    private fun uriOrNull(uriString: String?): Uri? {
        return if (!uriString.isNullOrEmpty()) {
            Uri.parse(uriString)
        } else {
            null
        }
    }

    private fun getImageUri(context: Context): Uri {
        val resources = context.resources

        val imageUri = Uri.Builder()
            .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
            .authority(resources.getResourcePackageName(R.drawable.android_cupcake))
            .appendPath(resources.getResourceTypeName(R.drawable.android_cupcake))
            .appendPath(resources.getResourceEntryName(R.drawable.android_cupcake))
            .build()

        return imageUri
    }

    internal fun setOutputUri(outputImageUri: String?) {
        outputUri = uriOrNull(outputImageUri)
    }

    class BlurViewModelFactory(private val application: Application) : ViewModelProvider.Factory {

        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            return if (modelClass.isAssignableFrom(BlurViewModel::class.java)) {
                BlurViewModel(application) as T
            } else {
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }
}
