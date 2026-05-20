package com.example.sherpastt

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * Sherpa-ONNX requires model files to be on the filesystem (not inside the APK/assets zip).
 * This utility copies the model from assets → internal storage on first run.
 */
object AssetUtils {

    private const val TAG = "AssetUtils"

    /**
     * Copies all files under [assetFolder] in assets to [context.filesDir]/[assetFolder].
     * Skips files that already exist (idempotent).
     *
     * @return absolute path of the copied model directory
     */
    fun copyModelToStorage(context: Context, assetFolder: String): String {
        val destDir = File(context.filesDir, assetFolder)
        if (!destDir.exists()) destDir.mkdirs()

        val assetManager = context.assets
        val files = assetManager.list(assetFolder) ?: emptyArray()

        for (fileName in files) {
            val destFile = File(destDir, fileName)
            if (destFile.exists()) {
                Log.d(TAG, "Skipping (already exists): $fileName")
                continue
            }
            Log.d(TAG, "Copying: $assetFolder/$fileName")
            assetManager.open("$assetFolder/$fileName").use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
        }

        Log.d(TAG, "Model ready at: ${destDir.absolutePath}")
        return destDir.absolutePath
    }

    /**
     * Returns true only when all required model files exist in internal storage.
     */
    fun isModelReady(context: Context, assetFolder: String): Boolean {
        val destDir = File(context.filesDir, assetFolder)
        val required = listOf(
            SherpaOnnxConfig.ENCODER,
            SherpaOnnxConfig.DECODER,
            SherpaOnnxConfig.JOINER,
            SherpaOnnxConfig.TOKENS
        )
        return required.all { File(destDir, it).exists() }
    }
}