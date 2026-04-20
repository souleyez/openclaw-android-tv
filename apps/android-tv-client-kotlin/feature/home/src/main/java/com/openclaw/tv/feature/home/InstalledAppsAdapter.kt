package com.openclaw.tv.feature.home

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

internal class InstalledAppsAdapter : RecyclerView.Adapter<InstalledAppsAdapter.InstalledAppViewHolder>() {

    private val items = mutableListOf<InstalledLaunchableAppItem>()
    private var onItemClick: ((InstalledLaunchableAppItem) -> Unit)? = null
    private var onItemFocus: ((Int, InstalledLaunchableAppItem) -> Unit)? = null
    private var iconResolver: InstalledAppIconResolver? = null

    fun submitList(nextItems: List<InstalledLaunchableAppItem>) {
        items.clear()
        items.addAll(nextItems)
        notifyDataSetChanged()
    }

    fun setOnItemClickListener(listener: (InstalledLaunchableAppItem) -> Unit) {
        onItemClick = listener
    }

    fun setOnItemFocusListener(listener: (Int, InstalledLaunchableAppItem) -> Unit) {
        onItemFocus = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InstalledAppViewHolder {
        if (iconResolver == null) {
            iconResolver = InstalledAppIconResolver(parent.context)
        }
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_installed_app, parent, false)
        return InstalledAppViewHolder(
            itemView = view,
            iconResolver = checkNotNull(iconResolver),
        )
    }

    override fun onBindViewHolder(holder: InstalledAppViewHolder, position: Int) {
        holder.bind(
            item = items[position],
            onItemClick = onItemClick,
            onItemFocus = onItemFocus,
        )
    }

    override fun getItemCount(): Int = items.size

    internal class InstalledAppViewHolder(
        itemView: View,
        private val iconResolver: InstalledAppIconResolver,
    ) : RecyclerView.ViewHolder(itemView) {
        private val card = itemView.findViewById<FrameLayout>(R.id.installed_app_card)
        private val topPanel = itemView.findViewById<FrameLayout>(R.id.installed_app_top_panel)
        private val surface = itemView.findViewById<View>(R.id.installed_app_surface)
        private val metaLabel = itemView.findViewById<TextView>(R.id.installed_app_meta_label)
        private val typeChip = itemView.findViewById<TextView>(R.id.installed_app_type_chip)
        private val iconPlate = itemView.findViewById<FrameLayout>(R.id.installed_app_icon_plate)
        private val accentDot = itemView.findViewById<View>(R.id.installed_app_accent_dot)
        private val icon = itemView.findViewById<ImageView>(R.id.installed_app_icon)
        private val title = itemView.findViewById<TextView>(R.id.installed_app_title)
        private val summary = itemView.findViewById<TextView>(R.id.installed_app_summary)
        private val hint = itemView.findViewById<TextView>(R.id.installed_app_hint)
        private var boundItem: InstalledLaunchableAppItem? = null
        private var onItemFocus: ((Int, InstalledLaunchableAppItem) -> Unit)? = null

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
            item: InstalledLaunchableAppItem,
            onItemClick: ((InstalledLaunchableAppItem) -> Unit)?,
            onItemFocus: ((Int, InstalledLaunchableAppItem) -> Unit)?,
        ) {
            boundItem = item
            this.onItemFocus = onItemFocus
            icon.setImageDrawable(iconResolver.resolve(item.packageName))
            title.text = item.title
            summary.text = item.summary
            metaLabel.text = if (item.isSystemApp) "系统入口" else "已安装应用"
            typeChip.text = if (item.isSystemApp) "系统" else "应用"
            hint.text = if (item.isSystemApp) "按确定键打开系统应用" else "按确定键打开应用"
            applyVisualState(item, card.hasFocus())
            card.setOnClickListener { onItemClick?.invoke(item) }
        }

        private fun applyVisualState(
            item: InstalledLaunchableAppItem,
            hasFocus: Boolean,
        ) {
            val accentColor = if (item.isSystemApp) Color.parseColor("#6FA6FF") else Color.parseColor("#FFAE61")
            accentDot.background = GradientDrawable().apply {
                cornerRadius = 999f
                setColor(accentColor)
            }
            topPanel.background = buildTopPanelBackground(accentColor, hasFocus)
            surface.background = buildTopPanelScrim(accentColor, hasFocus)
            iconPlate.background = buildIconPlateBackground(accentColor, hasFocus)
            typeChip.background = buildChipBackground(accentColor, hasFocus)
            hint.background = buildActionPillBackground(accentColor, hasFocus)
            card.background = GradientDrawable().apply {
                cornerRadius = 28f.dp(itemView.context)
                setColor(blend(accentColor, "#131A22", if (hasFocus) 0.14f else 0.08f))
                setStroke(
                    if (hasFocus) 2f.dp(itemView.context).toInt() else 1f.dp(itemView.context).toInt(),
                    if (hasFocus) accentColor else Color.parseColor("#2A3642"),
                )
            }
            metaLabel.alpha = if (hasFocus) 1f else 0.84f
            card.animate()
                .scaleX(if (hasFocus) 1.02f else 1f)
                .scaleY(if (hasFocus) 1.02f else 1f)
                .translationX(if (hasFocus) 10f else 0f)
                .setDuration(140L)
                .start()
            card.translationZ = if (hasFocus) 20f else 0f
        }

        private fun buildTopPanelBackground(accentColor: Int, hasFocus: Boolean): GradientDrawable {
            return GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    withAlpha(accentColor, if (hasFocus) 0.30f else 0.18f),
                    blend(accentColor, "#18212B", if (hasFocus) 0.14f else 0.08f),
                ),
            ).apply {
                cornerRadius = 24f.dp(itemView.context)
            }
        }

        private fun buildTopPanelScrim(accentColor: Int, hasFocus: Boolean): GradientDrawable {
            return GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(withAlpha(accentColor, if (hasFocus) 0.18f else 0.10f), Color.TRANSPARENT),
            ).apply {
                cornerRadius = 24f.dp(itemView.context)
            }
        }

        private fun buildIconPlateBackground(accentColor: Int, hasFocus: Boolean): GradientDrawable {
            return GradientDrawable().apply {
                cornerRadius = 20f.dp(itemView.context)
                setColor(withAlpha(accentColor, if (hasFocus) 0.24f else 0.14f))
                setStroke(1f.dp(itemView.context).toInt(), withAlpha(Color.WHITE, 0.14f))
            }
        }

        private fun buildChipBackground(accentColor: Int, hasFocus: Boolean): GradientDrawable {
            return GradientDrawable().apply {
                cornerRadius = 999f
                setColor(withAlpha(accentColor, if (hasFocus) 0.24f else 0.16f))
                setStroke(1f.dp(itemView.context).toInt(), withAlpha(accentColor, 0.56f))
            }
        }

        private fun buildActionPillBackground(accentColor: Int, hasFocus: Boolean): GradientDrawable {
            return GradientDrawable().apply {
                cornerRadius = 999f
                setColor(if (hasFocus) withAlpha(accentColor, 0.20f) else Color.parseColor("#1B232C"))
                setStroke(
                    1f.dp(itemView.context).toInt(),
                    if (hasFocus) withAlpha(accentColor, 0.70f) else withAlpha(Color.WHITE, 0.12f),
                )
            }
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

        private fun withAlpha(color: Int, alpha: Float): Int {
            val clamped = alpha.coerceIn(0f, 1f)
            return Color.argb(
                (255 * clamped).toInt(),
                Color.red(color),
                Color.green(color),
                Color.blue(color),
            )
        }

        private fun Float.dp(context: Context): Float {
            return this * context.resources.displayMetrics.density
        }
    }
}
