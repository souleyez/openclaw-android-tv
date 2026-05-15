package com.openclaw.tv.feature.cast

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.VideoView
import java.net.URL
import java.util.Locale

class DlnaPlaybackActivity : Activity() {
    private lateinit var statusView: TextView
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { }
    private var audioManager: AudioManager? = null
    private var hasAudioFocus = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        CastPlaybackInterrupter.interruptCurrentPlayback(this)
        requestCastAudioFocus()
        val mediaUri = intent.getStringExtra(EXTRA_MEDIA_URI)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let(Uri::parse)
        if (mediaUri == null) {
            finish()
            return
        }
        if (looksLikeImage(mediaUri)) {
            showImage(mediaUri)
        } else {
            showVideo(mediaUri)
        }
    }

    override fun onDestroy() {
        abandonCastAudioFocus()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun requestCastAudioFocus() {
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        hasAudioFocus = audioManager?.requestAudioFocus(
            audioFocusChangeListener,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN,
        ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    @Suppress("DEPRECATION")
    private fun abandonCastAudioFocus() {
        if (!hasAudioFocus) {
            return
        }
        audioManager?.abandonAudioFocus(audioFocusChangeListener)
        hasAudioFocus = false
    }

    private fun showVideo(uri: Uri) {
        val root = baseRoot()
        val videoView = VideoView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER,
            )
            setOnPreparedListener { player ->
                statusView.visibility = View.GONE
                player.start()
            }
            setOnErrorListener { _, _, _ ->
                statusView.text = "暂时无法播放这条投屏媒体"
                true
            }
        }
        root.addView(videoView, 0)
        setContentView(root)
        videoView.setVideoURI(uri)
    }

    private fun showImage(uri: Uri) {
        val root = baseRoot()
        val imageView = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER,
            )
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        root.addView(imageView, 0)
        setContentView(root)
        if (uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true)) {
            loadRemoteImage(uri, imageView)
        } else {
            imageView.setImageURI(uri)
            statusView.visibility = View.GONE
        }
    }

    private fun loadRemoteImage(
        uri: Uri,
        imageView: ImageView,
    ) {
        Thread {
            val bitmap = runCatching {
                URL(uri.toString()).openStream().use(BitmapFactory::decodeStream)
            }.getOrNull()
            runOnUiThread {
                if (bitmap == null) {
                    statusView.text = "暂时无法打开这张投屏图片"
                    return@runOnUiThread
                }
                imageView.setImageBitmap(bitmap)
                statusView.visibility = View.GONE
            }
        }.start()
    }

    private fun baseRoot(): FrameLayout {
        statusView = TextView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            )
            setBackgroundColor(Color.parseColor("#CC0B1017"))
            setPadding(28, 18, 28, 18)
            text = "正在接收手机投屏内容..."
            setTextColor(Color.WHITE)
            textSize = 20f
        }
        return FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(statusView)
        }
    }

    private fun looksLikeImage(uri: Uri): Boolean {
        val lower = uri.toString().lowercase(Locale.US)
        return lower.endsWith(".jpg") ||
            lower.endsWith(".jpeg") ||
            lower.endsWith(".png") ||
            lower.endsWith(".webp") ||
            lower.endsWith(".gif")
    }

    companion object {
        private const val EXTRA_MEDIA_URI = "com.openclaw.tv.feature.cast.EXTRA_MEDIA_URI"

        fun intent(
            context: Context,
            uri: String,
        ): Intent {
            return Intent(context, DlnaPlaybackActivity::class.java)
                .putExtra(EXTRA_MEDIA_URI, uri)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
