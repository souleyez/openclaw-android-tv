package com.openclaw.tv.feature.home

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

internal class WifiListAdapter : RecyclerView.Adapter<WifiListAdapter.WifiViewHolder>() {

    private val items = mutableListOf<WifiNetworkItem>()
    private var onItemClick: ((WifiNetworkItem) -> Unit)? = null
    private var onItemFocus: ((Int, WifiNetworkItem) -> Unit)? = null

    fun submitList(nextItems: List<WifiNetworkItem>) {
        items.clear()
        items.addAll(nextItems)
        notifyDataSetChanged()
    }

    fun setOnItemClickListener(listener: (WifiNetworkItem) -> Unit) {
        onItemClick = listener
    }

    fun setOnItemFocusListener(listener: (Int, WifiNetworkItem) -> Unit) {
        onItemFocus = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WifiViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_wifi_network, parent, false)
        return WifiViewHolder(view)
    }

    override fun onBindViewHolder(holder: WifiViewHolder, position: Int) {
        holder.bind(
            item = items[position],
            onItemClick = onItemClick,
            onItemFocus = onItemFocus,
        )
    }

    override fun getItemCount(): Int = items.size

    internal class WifiViewHolder(
        itemView: View,
    ) : RecyclerView.ViewHolder(itemView) {
        private val card = itemView.findViewById<FrameLayout>(R.id.wifi_card)
        private val topPanel = itemView.findViewById<FrameLayout>(R.id.wifi_top_panel)
        private val surface = itemView.findViewById<View>(R.id.wifi_surface)
        private val metaLabel = itemView.findViewById<TextView>(R.id.wifi_meta_label)
        private val iconPlate = itemView.findViewById<FrameLayout>(R.id.wifi_icon_plate)
        private val accentDot = itemView.findViewById<View>(R.id.wifi_accent_dot)
        private val iconBadge = itemView.findViewById<TextView>(R.id.wifi_icon_badge)
        private val title = itemView.findViewById<TextView>(R.id.wifi_title)
        private val summary = itemView.findViewById<TextView>(R.id.wifi_summary)
        private val status = itemView.findViewById<TextView>(R.id.wifi_status)
        private val actionHint = itemView.findViewById<TextView>(R.id.wifi_action_hint)
        private var boundItem: WifiNetworkItem? = null
        private var onItemFocus: ((Int, WifiNetworkItem) -> Unit)? = null

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
            item: WifiNetworkItem,
            onItemClick: ((WifiNetworkItem) -> Unit)?,
            onItemFocus: ((Int, WifiNetworkItem) -> Unit)?,
        ) {
            boundItem = item
            this.onItemFocus = onItemFocus
            title.text = item.ssid
            summary.text = item.summary
            status.text = item.statusLabel
            metaLabel.text = if (item.statusLabel == "当前") "当前识别" else "可用网络"
            actionHint.text = if (item.statusLabel == "当前") "按确定继续排查" else "按确定继续"
            applyVisualState(item, card.hasFocus())
            card.setOnClickListener { onItemClick?.invoke(item) }
        }

        private fun applyVisualState(
            item: WifiNetworkItem,
            hasFocus: Boolean,
        ) {
            val accentColor = if (item.statusLabel == "当前") Color.parseColor("#4EA6FF") else Color.parseColor("#7BD7A8")
            accentDot.background = GradientDrawable().apply {
                cornerRadius = 999f
                setColor(accentColor)
            }
            topPanel.background = buildTopPanelBackground(accentColor, hasFocus)
            surface.background = buildTopPanelScrim(accentColor, hasFocus)
            card.background = buildCardBackground(accentColor, hasFocus)
            iconPlate.background = buildIconPlateBackground(accentColor, hasFocus)
            status.background = buildChipBackground(accentColor, hasFocus)
            actionHint.background = buildActionPillBackground(accentColor, hasFocus)
            iconBadge.alpha = if (hasFocus) 1f else 0.92f
            metaLabel.alpha = if (hasFocus) 1f else 0.84f
            card.animate()
                .scaleX(if (hasFocus) 1.02f else 1f)
                .scaleY(if (hasFocus) 1.02f else 1f)
                .translationX(if (hasFocus) 8f else 0f)
                .setDuration(140L)
                .start()
            card.translationZ = if (hasFocus) 20f else 0f
        }

        private fun buildCardBackground(accentColor: Int, hasFocus: Boolean): GradientDrawable {
            return GradientDrawable().apply {
                cornerRadius = 28f.dp(itemView.context)
                setColor(blend(accentColor, "#131A22", if (hasFocus) 0.14f else 0.08f))
                setStroke(
                    if (hasFocus) 2f.dp(itemView.context).toInt() else 1f.dp(itemView.context).toInt(),
                    if (hasFocus) accentColor else Color.parseColor("#2A3642"),
                )
            }
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
                setColor(withAlpha(accentColor, if (hasFocus) 0.28f else 0.18f))
                setStroke(1f.dp(itemView.context).toInt(), withAlpha(accentColor, 0.60f))
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
