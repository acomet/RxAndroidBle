package com.polidea.rxandroidble2.samplekotlin.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.widget.Toast
import com.polidea.rxandroidble2.samplekotlin.SampleApplication

/**
 * toast
 */
fun toast(message: String? = "") {
    Toast.makeText(SampleApplication.appContext, message, Toast.LENGTH_SHORT).show()
}

/**
 * 复制
 */
fun copy(string: String? = "") {
    val cm = SampleApplication.appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(null, string))
    toast("复制成功")
}

/**
 * 打印
 */
fun logger(msg: String? = "") {
    if (!msg.isNullOrEmpty()) {
        Log.i("RxBle", msg)
    }
}

const val FAST_CLICK_DELAY_TIME = 1000
private var lastClickTime: Long = 0

@Synchronized
fun isFastClick(): Boolean {
    var flag = false
    val currentClickTime = System.currentTimeMillis()
    if (currentClickTime - lastClickTime <= FAST_CLICK_DELAY_TIME) {
        flag = true
    }
    lastClickTime = currentClickTime
    return flag
}



