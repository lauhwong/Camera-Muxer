package com.miracles.mediacodec

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import kotlinx.android.synthetic.main.activity_preview.*
import java.io.File

/**
 * Created by lxw
 */
class PreviewActivity : BaseActivity() {
    companion object {
        private const val PARAMS_PATH = "path"
        private const val PARAMS_FLAG_PICTURE = "flag.picture"
        fun start(context: Context, path: String, flagPicture: Boolean) {
            val intent = Intent(context, PreviewActivity::class.java)
            intent.putExtra(PARAMS_PATH, path)
            intent.putExtra(PARAMS_FLAG_PICTURE, flagPicture)
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preview)
        val intent = intent
        val path = intent.getStringExtra(PARAMS_PATH)
        val flagPicture = intent.getBooleanExtra(PARAMS_FLAG_PICTURE, true)
        if (path == null || !File(path).exists()) {
            finish()
        } else {
            initView(path, flagPicture)
        }
    }

    private fun initView(path: String, flagPicture: Boolean) {
        if (flagPicture) {
            ivPreview.visibility = View.VISIBLE
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            BitmapFactory.decodeFile(path, options)
            val sampleSize = 1080 / options.outWidth
            options.inJustDecodeBounds = false
            if (sampleSize > 1) {
                options.inSampleSize = sampleSize
            }
            ivPreview.setImageBitmap(BitmapFactory.decodeFile(path, options))
        } else {
            vvPreview.visibility = View.VISIBLE
            vvPreview.setDataSource(this, Uri.parse(path))
            vvPreview.prepareAsync{
                mp ->
                Handler(Looper.getMainLooper()).post{
                    mp.start()
                }
            }
            vvPreview.isLooping=true
        }
    }
}