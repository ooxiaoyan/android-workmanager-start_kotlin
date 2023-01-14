package com.example.background.workers

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.text.TextUtils
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.background.KEY_IMAGE_URI

private const val TAG = "BlurWorker"

class BlurWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val appContext = applicationContext
        val resourceUri = inputData.getString(KEY_IMAGE_URI) // 获取从 Data 对象传入的 URI
        makeStatusNotification("开始图片模糊处理", appContext)

        sleep() // 使用 WorkerUtils 类中定义的 sleep() 方法减慢工作速度，以便更轻松地做到查看每个 WorkRequest 的启动情况

        return try {
            if (TextUtils.isEmpty(resourceUri)) {
                Log.e(TAG, "Invalid input uri")
                throw IllegalArgumentException("Invalid input uri")
            }
            val resolver = appContext.contentResolver
            val picture = BitmapFactory.decodeStream(
                resolver.openInputStream(Uri.parse(resourceUri))
            )

            val output = blurBitmap(picture, appContext) // 获取位图模糊处理后的版本
            val outputUri = writeBitmapToFile(appContext, output) // 将该位图写入临时文件
            makeStatusNotification("uri=$outputUri", appContext)
            val outputData = workDataOf(KEY_IMAGE_URI to outputUri.toString())
            Result.success(outputData)
        } catch (e: Exception) {
            Log.e(TAG, "Error applying blur")
            e.printStackTrace()
            Result.failure()
        }
    }
}