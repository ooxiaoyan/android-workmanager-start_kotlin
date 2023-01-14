package com.example.background.workers

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.background.R

private const val TAG = "BlurWorker"

class BlurWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val appContext = applicationContext
        makeStatusNotification("开始图片模糊处理", appContext)
        return try {
            val picture = BitmapFactory.decodeResource(
                appContext.resources,
                R.drawable.android_cupcake
            )
            val blurBitmap = blurBitmap(picture, appContext) // 获取位图模糊处理后的版本
            val uri = writeBitmapToFile(appContext, blurBitmap) // 将该位图写入临时文件
            makeStatusNotification("uri=$uri", appContext)
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error applying blur")
            Result.failure()
        }
    }
}