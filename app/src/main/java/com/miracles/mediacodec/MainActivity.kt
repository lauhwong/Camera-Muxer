package com.miracles.mediacodec

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {
    companion object {
        private const val CODE_REQUEST_PERMISSION = 301
    }

    private val mDangerousPermissions = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO
            , Manifest.permission.CAMERA)
    private val mDeniedPermissions = arrayListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        btStart.setOnClickListener(this::start)
    }

    private fun start(view: View) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            go2CameraView()
        } else {
            val permissions = getNotPermitted(this, mDangerousPermissions)
            if (permissions.isEmpty()) {
                go2CameraView()
            } else {
                mDeniedPermissions.addAll(permissions)
                requestPermissions(permissions.toTypedArray(), CODE_REQUEST_PERMISSION)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (verifyPermissionResults(requestCode, permissions, grantResults)) {
            go2CameraView()
        } else {
            Toast.makeText(this, "权限不足！${mDeniedPermissions.joinToString()}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getNotPermitted(context: Context, permissions: Array<String>): List<String> {
        val result = arrayListOf<String>()
        for (p in permissions) {
            val permit = ContextCompat.checkSelfPermission(context, p)
            if (permit == PackageManager.PERMISSION_DENIED) {
                result.add(p)
            }
        }
        return result
    }

    private fun verifyPermissionResults(requestCode: Int, permissions: Array<String>, grantResults: IntArray): Boolean {
        if (requestCode != CODE_REQUEST_PERMISSION) return false
        for ((i, p) in permissions.withIndex()) {
            if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                mDeniedPermissions.remove(p)
            }
        }
        return mDeniedPermissions.isEmpty()
    }

    private fun go2CameraView() {
        CameraActivity.start(this)
    }
}
