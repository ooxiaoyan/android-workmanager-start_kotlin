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
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.work.*
import com.example.background.workers.BlurWorker
import com.example.background.workers.CleanupWorker
import com.example.background.workers.SaveImageToFileWorker

// 此视图模型用于存储显示 BlurActivity 所需的所有数据，也将是您使用 WorkManager 启动后台工作的类。
class BlurViewModel(application: Application) : ViewModel() {

    internal var imageUri: Uri? = null
    internal var outputUri: Uri? = null
    // 您可以通过获取保留 WorkInfo 对象的 LiveData 来获取任何 WorkRequest 的状态。
    // WorkInfo 是一个包含 WorkRequest 当前状态详细信息的对象，其中包括：
    // 工作是否为 BLOCKED、CANCELLED、ENQUEUED、FAILED、RUNNING 或 SUCCEEDED。
    // 如果 WorkRequest 完成，则为工作的任何输出数据。
    internal val outputWorkInfos: LiveData<List<WorkInfo>>

    private val workManager = WorkManager.getInstance(application)

    init {
        imageUri = getImageUri(application.applicationContext)
        // 使用 WorkManager.getWorkInfosByTagLiveData 获取 WorkInfo
        outputWorkInfos = workManager.getWorkInfosByTagLiveData(TAG_OUTPUT)
    }

    /**
     * Create the WorkRequest to apply the blur and save the resulting image
     * @param blurLevel The amount to blur the image
     */
    internal fun applyBlur(blurLevel: Int) {
        // 创建 WorkRequest 链
        // 添加 WorkRequest 清理临时文件
        // beginUniqueWork 确保工作链是唯一的（一次只会对一张图片进行模糊处理）
        //   参数-uniqueWorkName：唯一的 String 名称。这会命名整个工作请求链，以便您一起引用和查询这些请求
        //   参数-existingWorkPolicy：选项包括 REPLACE、KEEP 或 APPEND
        var continuation = workManager.beginUniqueWork(
                IMAGE_MANIPULATION_WORK_NAME,
                ExistingWorkPolicy.REPLACE, // 因为如果用户在当前图片完成之前决定对另一张图片进行模糊处理，我们需要停止当前图片并开始对新图片进行模糊处理。
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

        // 创建约束条件：使用设备必须充电（工作请求只会在设备充电的情况下运行）
        val constraints = Constraints.Builder()
            .setRequiresCharging(true)
            .build()

        // 添加 WorkRequest 保存图片
        val save = OneTimeWorkRequest.Builder(SaveImageToFileWorker::class.java)
            .setConstraints(constraints) // 设置约束条件，当设备不充电时，应会暂停执行 SaveImageToFileWorker，直到您将设备插入充电。
            .addTag(TAG_OUTPUT) // 为 WorkRequest 添加标记
            .build()

        continuation = continuation.then(save)

        // Actually start the work
        continuation.enqueue()
    }

    // 按唯一链名称取消工作，因为想要取消链中的所有工作，而不仅仅是某个特定步骤。
    // 由于 WorkState 不再处于“FINISHED”（已完成）状态，因此工作取消后，只有“GO”（开始）按钮。
    internal fun cancelWork() {
        workManager.cancelUniqueWork(IMAGE_MANIPULATION_WORK_NAME)
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
