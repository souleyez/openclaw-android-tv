package com.openclaw.tv.feature.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.annotation.DrawableRes

class AssistantSpriteView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val sourceRect = Rect()
    private val destinationRect = RectF()
    private var atlasBitmap: Bitmap? = null
    private var activeState = AssistantSpriteState.SUMMER_IDLE
    private var activeResId = 0
    private var frameIndex = 0
    private var tickerRunning = false
    private var offlineTreatment = false

    private val ticker = object : Runnable {
        override fun run() {
            if (!shouldAnimate()) {
                tickerRunning = false
                return
            }
            val nextFrameIndex = (frameIndex + 1) % activeState.frameCount
            val completedLoop = nextFrameIndex == 0
            frameIndex = nextFrameIndex
            invalidate()
            postDelayed(this, resolveNextFrameDelay(completedLoop))
        }
    }

    init {
        contentDescription = null
    }

    fun setSpriteState(state: AssistantSpriteState) {
        if (activeState == state && atlasBitmap != null) {
            ensureTicker()
            return
        }
        activeState = state
        frameIndex = 0
        removeCallbacks(ticker)
        tickerRunning = false
        loadAtlas(state.atlasResId)
        invalidate()
        ensureTicker()
    }

    fun setOfflineTreatment(enabled: Boolean) {
        if (offlineTreatment == enabled) {
            return
        }
        offlineTreatment = enabled
        paint.colorFilter = if (enabled) offlineColorFilter() else null
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        loadAtlas(activeState.atlasResId)
        ensureTicker()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(ticker)
        tickerRunning = false
        recycleAtlas()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(
        changedView: View,
        visibility: Int,
    ) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) {
            ensureTicker()
        } else {
            removeCallbacks(ticker)
            tickerRunning = false
        }
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) {
            ensureTicker()
        } else {
            removeCallbacks(ticker)
            tickerRunning = false
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bitmap = atlasBitmap ?: loadAtlas(activeState.atlasResId) ?: return
        val frameCount = activeState.frameCount.coerceAtLeast(1)
        val frameWidth = bitmap.width / frameCount
        val frameHeight = bitmap.height
        val safeFrameIndex = frameIndex.coerceIn(0, frameCount - 1)
        sourceRect.set(
            safeFrameIndex * frameWidth,
            0,
            (safeFrameIndex + 1) * frameWidth,
            frameHeight,
        )
        val scale = minOf(
            width.toFloat() / frameWidth.toFloat(),
            height.toFloat() / frameHeight.toFloat(),
        )
        val drawWidth = frameWidth * scale
        val drawHeight = frameHeight * scale
        val left = (width - drawWidth) / 2f
        val top = height - drawHeight
        destinationRect.set(left, top, left + drawWidth, top + drawHeight)
        canvas.drawBitmap(bitmap, sourceRect, destinationRect, paint)
    }

    private fun ensureTicker() {
        if (tickerRunning || !shouldAnimate()) {
            return
        }
        tickerRunning = true
        removeCallbacks(ticker)
        postDelayed(ticker, activeState.frameDurationMs)
    }

    private fun shouldAnimate(): Boolean {
        return isAttachedToWindow &&
            visibility == VISIBLE &&
            windowVisibility == VISIBLE &&
            activeState.frameCount > 1
    }

    private fun resolveNextFrameDelay(completedLoop: Boolean): Long {
        return if (completedLoop && activeState.loopPauseMs > 0L) {
            activeState.loopPauseMs
        } else {
            activeState.frameDurationMs
        }
    }

    private fun loadAtlas(@DrawableRes resId: Int): Bitmap? {
        if (activeResId == resId && atlasBitmap != null) {
            return atlasBitmap
        }
        recycleAtlas()
        activeResId = resId
        atlasBitmap = BitmapFactory.decodeResource(resources, resId)
            ?: BitmapFactory.decodeResource(resources, R.drawable.assistant_girl_hero_bust)
        return atlasBitmap
    }

    private fun recycleAtlas() {
        atlasBitmap?.recycle()
        atlasBitmap = null
        activeResId = 0
    }

    private fun offlineColorFilter(): ColorMatrixColorFilter {
        val saturationMatrix = ColorMatrix().apply { setSaturation(0.82f) }
        val coolToneMatrix = ColorMatrix().apply { setScale(0.88f, 0.93f, 1.05f, 1f) }
        saturationMatrix.postConcat(coolToneMatrix)
        return ColorMatrixColorFilter(saturationMatrix)
    }
}
