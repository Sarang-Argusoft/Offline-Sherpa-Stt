package com.example.sherpastt

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin
import kotlin.random.Random

/**
 * Simple animated waveform bar drawn with Canvas.
 * Automatically animates while visible, stops when invisible.
 * No external dependencies — pure Android drawing.
 */
class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.accent)
        style = Paint.Style.FILL
    }

    private val barCount    = 20
    private val barWidthDp  = 4f
    private val barSpaceDp  = 3f
    private val cornerRadius = 8f

    private val barWidth  = barWidthDp  * resources.displayMetrics.density
    private val barSpace  = barSpaceDp  * resources.displayMetrics.density

    private val amplitudes = FloatArray(barCount) { Random.nextFloat() }
    private var phase = 0.0

    private val handler = Handler(Looper.getMainLooper())
    private val animRunnable = object : Runnable {
        override fun run() {
            phase += 0.15
            for (i in amplitudes.indices) {
                // Smooth organic wave: sine + small random perturbation
                amplitudes[i] = ((sin(phase + i * 0.5) * 0.4 + 0.5) +
                        Random.nextFloat() * 0.15f).toFloat().coerceIn(0.1f, 1.0f)
            }
            invalidate()
            handler.postDelayed(this, 60L)  // ~16 fps
        }
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) {
            handler.post(animRunnable)
        } else {
            handler.removeCallbacks(animRunnable)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(animRunnable)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val totalWidth  = barCount * (barWidth + barSpace) - barSpace
        val startX      = (width - totalWidth) / 2f
        val centerY     = height / 2f

        for (i in 0 until barCount) {
            val x       = startX + i * (barWidth + barSpace)
            val barH    = amplitudes[i] * height * 0.9f
            val rect    = RectF(x, centerY - barH / 2, x + barWidth, centerY + barH / 2)
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
        }
    }
}