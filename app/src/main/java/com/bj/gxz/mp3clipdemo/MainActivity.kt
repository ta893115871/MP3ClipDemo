package com.bj.gxz.mp3clipdemo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.view.View
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.channels.FileChannel

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        checkPermission()
    }

    // 简单的处理下权限
    fun checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.RECORD_AUDIO,
                ), 1
            )
        }
    }

    @Throws(IOException::class)
    private fun copyAssets(assetsName: String, path: String) {
        val assetFileDescriptor = assets.openFd(assetsName)
        val from: FileChannel = FileInputStream(assetFileDescriptor.fileDescriptor).getChannel()
        val to: FileChannel = FileOutputStream(path).getChannel()
        from.transferTo(assetFileDescriptor.startOffset, assetFileDescriptor.length, to)
        from.close()
        to.close()
    }

    fun clip(view: View) {
        Thread {
            val src = Environment.getExternalStorageDirectory().absolutePath + "/441k_2.mp3"
            copyAssets("441k_2.mp3", src)
            val out = Environment.getExternalStorageDirectory().absolutePath + "/441k_2.wav"
            Clip.clip(src, out, 15 * 1000 * 1000, 25 * 1000 * 1000)
        }.start()
    }
}