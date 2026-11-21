package com.dichgame.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build

/** Nhận kết quả từ trình cài đặt của Android: hiện hộp thoại xác nhận khi cần, báo lỗi nếu có. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val status = i.getIntExtra(PackageInstaller.EXTRA_STATUS, -999)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val ask: Intent? = if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java) else i.getParcelableExtra(Intent.EXTRA_INTENT)
            if (ask != null) {
                try {
                    c.startActivity(ask.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) {
                    Updater.installStatus(PackageInstaller.STATUS_FAILURE, "không mở được hộp thoại xác nhận của Android")
                }
            }
            return
        }
        Updater.installStatus(status, i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty())
    }
}
