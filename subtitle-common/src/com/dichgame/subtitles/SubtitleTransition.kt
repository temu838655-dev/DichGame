package com.dichgame.subtitles

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.widget.TextView

/** Animate only the previous caption. A newer render supersedes its pending animation. */
internal object SubtitleTransition {
    private val active = java.util.WeakHashMap<TextView, ValueAnimator>()

    fun display(view: TextView, caption: SpannableStringBuilder, previousEnd: Int, remaining: Long) {
        active.remove(view)?.cancel()
        if (previousEnd == 0) {
            view.text = caption
            return
        }
        var progress = 0f
        val fade = object : CharacterStyle() {
            override fun updateDrawState(paint: TextPaint) {
                paint.alpha = (paint.alpha * (1f - progress)).toInt()
                paint.baselineShift -= (paint.textSize * 0.3f * progress).toInt()
            }
        }
        caption.setSpan(fade, 0, previousEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        view.setText(caption, TextView.BufferType.SPANNABLE)
        val displayed = view.text as Spannable
        val durationMs = remaining.coerceIn(1L, 350L)
        val animation = ValueAnimator.ofFloat(0f, 1f)
        animation.duration = durationMs
        animation.startDelay = (remaining - durationMs).coerceAtLeast(0)
        animation.addUpdateListener {
            progress = it.animatedValue as Float
            if (view.text === displayed) view.invalidate()
        }
        animation.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animator: Animator) {
                if (active[view] !== animator) return
                active.remove(view)
                if (view.text === displayed) view.text = displayed.subSequence(previousEnd, displayed.length)
            }
        })
        active[view] = animation
        animation.start()
    }
}
