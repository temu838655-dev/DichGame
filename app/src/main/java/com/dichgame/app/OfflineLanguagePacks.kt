package com.dichgame.app

import android.os.SystemClock
import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel

/**
 * Tải sớm các model cần cho Anh/Trung → Việt, không chặn UI.
 * English đã tích hợp trong ML Kit, chỉ cần tải Vietnamese và Chinese.
 * Gọi từ main thread; mỗi tiến trình chỉ có một lượt tải, các màn hình dùng chung kết quả.
 */
object OfflineLanguagePacks {
    private var loading = false
    private var ready = false
    private var lastAttempt = -60_000L
    private val callbacks = mutableListOf<(Boolean) -> Unit>()

    fun ensure(force: Boolean = false, done: ((Boolean) -> Unit)? = null) {
        if (loading) { if (done != null) callbacks.add(done); return }
        if (ready && !force) { done?.invoke(true); return }
        if (!force && SystemClock.elapsedRealtime() - lastAttempt < 60_000L) {
            done?.invoke(false); return
        }
        lastAttempt = SystemClock.elapsedRealtime()
        loading = true
        if (done != null) callbacks.add(done)
        val manager = RemoteModelManager.getInstance()
        val languages = listOf(TranslateLanguage.VIETNAMESE, TranslateLanguage.CHINESE)
        val conditions = DownloadConditions.Builder().build()

        fun finish(ok: Boolean) {
            loading = false
            ready = ok
            val waiting = callbacks.toList()
            callbacks.clear()
            for (cb in waiting) cb(ok)
            if (ok) {
                // Dọn model dịch cũ của chính ứng dụng, không đụng vào gói Việt hóa hay giọng đọc.
                manager.getDownloadedModels(TranslateRemoteModel::class.java)
                    .addOnSuccessListener { models ->
                        for (model in models) {
                            if (model.language !in languages && model.language != TranslateLanguage.ENGLISH) {
                                manager.deleteDownloadedModel(model).addOnFailureListener { Log.w("DG_LANG", "Không dọn được model cũ", it) }
                            }
                        }
                    }
            }
        }

        fun next(index: Int, allOk: Boolean) {
            if (index >= languages.size) { finish(allOk); return }
            val model = TranslateRemoteModel.Builder(languages[index]).build()
            manager.isModelDownloaded(model)
                .addOnSuccessListener { installed ->
                    if (installed) next(index + 1, allOk)
                    else manager.download(model, conditions)
                        .addOnSuccessListener { next(index + 1, allOk) }
                        .addOnFailureListener { next(index + 1, false) }
                }
                .addOnFailureListener { next(index + 1, false) }
        }
        next(0, true)
    }
}
