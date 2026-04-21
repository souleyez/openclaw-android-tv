package com.openclaw.tv.feature.home

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

internal class AppRailAdapter : RecyclerView.Adapter<AppRailAdapter.AppViewHolder>() {

    private val items = mutableListOf<FeaturedAppItem>()
    private var onItemClick: ((FeaturedAppItem) -> Unit)? = null
    private var onItemFocus: ((Int, FeaturedAppItem) -> Unit)? = null
    private var iconResolver: InstalledAppIconResolver? = null

    fun submitList(nextItems: List<FeaturedAppItem>) {
        items.clear()
        items.addAll(nextItems)
        notifyDataSetChanged()
    }

    fun setOnItemClickListener(listener: (FeaturedAppItem) -> Unit) {
        onItemClick = listener
    }

    fun setOnItemFocusListener(listener: (Int, FeaturedAppItem) -> Unit) {
        onItemFocus = listener
    }

    fun getItem(position: Int): FeaturedAppItem? = items.getOrNull(position)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        if (iconResolver == null) {
            iconResolver = InstalledAppIconResolver(parent.context)
        }
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_rail, parent, false)
        return AppViewHolder(
            itemView = view,
            iconResolver = checkNotNull(iconResolver),
        )
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(items[position], onItemClick, onItemFocus)
    }

    override fun getItemCount(): Int = items.size

    class AppViewHolder(
        itemView: View,
        private val iconResolver: InstalledAppIconResolver,
    ) : RecyclerView.ViewHolder(itemView) {
        private val card = itemView.findViewById<FrameLayout>(R.id.app_card)
        private val previewPanel = itemView.findViewById<FrameLayout>(R.id.app_preview_panel)
        private val previewScrim = itemView.findViewById<View>(R.id.app_preview_scrim)
        private val metaLabel = itemView.findViewById<TextView>(R.id.app_meta_label)
        private val accentBar = itemView.findViewById<View>(R.id.app_accent_bar)
        private val iconPlate = itemView.findViewById<FrameLayout>(R.id.app_icon_plate)
        private val icon = itemView.findViewById<ImageView>(R.id.app_icon)
        private val badge = itemView.findViewById<TextView>(R.id.app_icon_badge)
        private val statusChip = itemView.findViewById<TextView>(R.id.app_status_chip)
        private val title = itemView.findViewById<TextView>(R.id.app_title)
        private val summary = itemView.findViewById<TextView>(R.id.app_summary)
        private val actionHint = itemView.findViewById<TextView>(R.id.app_action_hint)
        private var boundItem: FeaturedAppItem? = null
        private var onItemFocus: ((Int, FeaturedAppItem) -> Unit)? = null

        init {
            card.setOnFocusChangeListener { _, hasFocus ->
                boundItem?.let { item ->
                    applyVisualState(item, hasFocus)
                    if (hasFocus) {
                        val adapterPosition = bindingAdapterPosition
                        if (adapterPosition != RecyclerView.NO_POSITION) {
                            onItemFocus?.invoke(adapterPosition, item)
                        }
                    }
                }
            }
        }

        fun bind(
            item: FeaturedAppItem,
            onItemClick: ((FeaturedAppItem) -> Unit)?,
            onItemFocus: ((Int, FeaturedAppItem) -> Unit)?,
        ) {
            boundItem = item
            this.onItemFocus = onItemFocus
            metaLabel.text = resolveMetaLabel(item)
            title.text = item.title
            summary.text = item.summary
            statusChip.text = item.statusLabel
            actionHint.text = item.actionLabel
            summary.visibility = View.GONE
            actionHint.visibility = View.GONE
            bindIcon(item)
            applyVisualState(item, card.hasFocus())
            card.setOnClickListener { onItemClick?.invoke(item) }
        }

        private fun bindIcon(item: FeaturedAppItem) {
            val iconDrawable = if (item.installed) {
                iconResolver.resolve(item.packageName)
            } else {
                null
            }
            if (iconDrawable != null) {
                icon.setImageDrawable(iconDrawable)
                icon.visibility = View.VISIBLE
                badge.visibility = View.GONE
            } else {
                icon.visibility = View.GONE
                badge.visibility = View.VISIBLE
                badge.text = item.monogram
            }
        }

        private fun applyVisualState(item: FeaturedAppItem, hasFocus: Boolean) {
            val accentColor = Color.parseColor(item.accentColorHex)
            accentBar.background = GradientDrawable().apply {
                cornerRadius = 999f
                setColor(accentColor)
            }
            previewPanel.background = buildPreviewPanelBackground(accentColor, item.installed, hasFocus)
            previewScrim.background = buildPreviewScrimBackground(accentColor, item.installed, hasFocus)
            card.background = buildCardBackground(accentColor, item.installed, hasFocus)
            iconPlate.background = buildIconPlateBackground(accentColor, item.installed, hasFocus)
            statusChip.background = buildChipBackground(accentColor, item.installed, hasFocus)
            actionHint.background = buildActionPillBackground(accentColor, item.installed, hasFocus)
            badge.setTextColor(Color.WHITE)
            metaLabel.alpha = if (hasFocus) 1f else 0.82f
            title.alpha = if (item.installed || hasFocus) 1f else 0.94f
            summary.alpha = if (item.installed || hasFocus) 0.96f else 0.82f
            actionHint.alpha = if (item.installed || hasFocus) 1f else 0.76f
            card.animate()
                .scaleX(if (hasFocus) 1.035f else 1f)
                .scaleY(if (hasFocus) 1.035f else 1f)
                .translationY(if (hasFocus) -6f else 0f)
                .setDuration(140L)
                .start()
            card.translationZ = if (hasFocus) 18f else 0f
        }

        private fun buildCardBackground(
            accentColor: Int,
            installed: Boolean,
            hasFocus: Boolean,
        ): GradientDrawable {
            val fillColor = when {
                hasFocus -> blend(accentColor, "#18202A", 0.34f)
                installed -> blend(accentColor, "#151D27", 0.24f)
                else -> Color.parseColor("#18202A")
            }
            val strokeColor = when {
                hasFocus -> accentColor
                installed -> withAlpha(accentColor, 0.44f)
                else -> Color.parseColor("#2A3643")
            }
            return GradientDrawable().apply {
                cornerRadius = 20f.dp(itemView.context)
                setColor(fillColor)
                setStroke(
                    if (hasFocus) 2f.dp(itemView.context).toInt() else 1f.dp(itemView.context).toInt(),
                    strokeColor,
                )
            }
        }

        private fun buildPreviewPanelBackground(
            accentColor: Int,
            installed: Boolean,
            hasFocus: Boolean,
        ): GradientDrawable {
            val startColor = when {
                hasFocus -> withAlpha(accentColor, 0.62f)
                installed -> withAlpha(accentColor, 0.48f)
                else -> Color.parseColor("#2B3744")
            }
            val endColor = when {
                hasFocus -> blend(accentColor, "#17202A", 0.30f)
                installed -> blend(accentColor, "#17202A", 0.22f)
                else -> Color.parseColor("#18212B")
            }
            return GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(startColor, endColor),
            ).apply {
                cornerRadius = 20f.dp(itemView.context)
            }
        }

        private fun buildPreviewScrimBackground(
            accentColor: Int,
            installed: Boolean,
            hasFocus: Boolean,
        ): GradientDrawable {
            val glowColor = when {
                hasFocus -> withAlpha(accentColor, 0.22f)
                installed -> withAlpha(accentColor, 0.15f)
                else -> withAlpha(Color.WHITE, 0.05f)
            }
            return GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(glowColor, Color.TRANSPARENT),
            ).apply {
                cornerRadius = 20f.dp(itemView.context)
            }
        }

        private fun buildIconPlateBackground(
            accentColor: Int,
            installed: Boolean,
            hasFocus: Boolean,
        ): GradientDrawable {
            val fillColor = when {
                hasFocus -> withAlpha(accentColor, 0.28f)
                installed -> withAlpha(accentColor, 0.18f)
                else -> Color.parseColor("#24303B")
            }
            return GradientDrawable().apply {
                cornerRadius = 18f.dp(itemView.context)
                setColor(fillColor)
                setStroke(1f.dp(itemView.context).toInt(), withAlpha(Color.WHITE, 0.14f))
            }
        }

        private fun buildChipBackground(
            accentColor: Int,
            installed: Boolean,
            hasFocus: Boolean,
        ): GradientDrawable {
            return GradientDrawable().apply {
                cornerRadius = 999f
                setColor(
                    when {
                        hasFocus -> withAlpha(accentColor, 0.28f)
                        installed -> withAlpha(accentColor, 0.18f)
                        else -> Color.parseColor("#24303A")
                    },
                )
                setStroke(
                    1f.dp(itemView.context).toInt(),
                    if (installed || hasFocus) withAlpha(accentColor, 0.56f) else withAlpha(Color.WHITE, 0.10f),
                )
            }
        }

        private fun buildActionPillBackground(
            accentColor: Int,
            installed: Boolean,
            hasFocus: Boolean,
        ): GradientDrawable {
            return GradientDrawable().apply {
                cornerRadius = 999f
                setColor(
                    when {
                        hasFocus -> withAlpha(accentColor, 0.24f)
                        installed -> Color.parseColor("#202A34")
                        else -> Color.parseColor("#1A222B")
                    },
                )
                setStroke(
                    1f.dp(itemView.context).toInt(),
                    if (hasFocus) withAlpha(accentColor, 0.72f) else withAlpha(Color.WHITE, 0.12f),
                )
            }
        }

        private fun resolveMetaLabel(item: FeaturedAppItem): String {
            return when (item.appId) {
                "bilibili" -> "社区内容"
                "youtube" -> "视频平台"
                else -> "主流节目"
            }
        }

        private fun withAlpha(color: Int, alpha: Float): Int {
            val clamped = alpha.coerceIn(0f, 1f)
            return Color.argb(
                (255 * clamped).toInt(),
                Color.red(color),
                Color.green(color),
                Color.blue(color),
            )
        }

        private fun blend(accentColor: Int, baseHex: String, accentWeight: Float): Int {
            val baseColor = Color.parseColor(baseHex)
            val weight = accentWeight.coerceIn(0f, 1f)
            val inverse = 1f - weight
            return Color.rgb(
                (Color.red(accentColor) * weight + Color.red(baseColor) * inverse).toInt(),
                (Color.green(accentColor) * weight + Color.green(baseColor) * inverse).toInt(),
                (Color.blue(accentColor) * weight + Color.blue(baseColor) * inverse).toInt(),
            )
        }

        private fun Float.dp(context: Context): Float {
            return this * context.resources.displayMetrics.density
        }
    }
}

internal class InstalledAppIconResolver(
    context: Context,
) {
    private val packageManager = context.applicationContext.packageManager
    private val iconCache = linkedMapOf<String, Drawable?>()

    fun resolve(packageName: String): Drawable? {
        if (iconCache.containsKey(packageName)) {
            return iconCache[packageName]
        }
        val resolved = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationIcon(packageName)
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationIcon(packageName)
            }
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
        if (iconCache.size >= 24) {
            val firstKey = iconCache.keys.firstOrNull()
            if (firstKey != null) {
                iconCache.remove(firstKey)
            }
        }
        iconCache[packageName] = resolved
        return resolved
    }
}
