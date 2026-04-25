package com.openclaw.tv.feature.home

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
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
        private val surface = itemView.findViewById<LinearLayout>(R.id.wifi_surface)
        private val iconPlate = itemView.findViewById<FrameLayout>(R.id.wifi_icon_plate)
        private val accentDot = itemView.findViewById<View>(R.id.wifi_accent_dot)
        private val iconBadge = itemView.findViewById<TextView>(R.id.wifi_icon_badge)
        private val title = itemView.findViewById<TextView>(R.id.wifi_title)
        private val status = itemView.findViewById<TextView>(R.id.wifi_status)
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
            status.text = if (item.statusLabel == "当前") "当前" else "可连"
            applyVisualState(item, card.hasFocus())
            card.setOnClickListener { onItemClick?.invoke(item) }
        }

        private fun applyVisualState(
            item: WifiNetworkItem,
            hasFocus: Boolean,
        ) {
            val accentColor = if (item.statusLabel == "当前") {
                Color.parseColor("#79B8FF")
            } else {
                Color.parseColor("#DDE8F5")
            }
            card.background = GradientDrawable().apply {
                cornerRadius = 16f.dp(itemView.context)
                setColor(if (hasFocus) Color.parseColor("#4A5E7B") else Color.parseColor("#334258"))
                setStroke(
                    if (hasFocus) 2f.dp(itemView.context).toInt() else 1f.dp(itemView.context).toInt(),
                    if (hasFocus) Color.parseColor("#C9E1FF") else Color.parseColor("#637895"),
                )
            }
            surface.alpha = if (hasFocus) 1f else 0.94f
            iconPlate.background = GradientDrawable().apply {
                cornerRadius = 13f.dp(itemView.context)
                setColor(if (hasFocus) Color.parseColor("#58729A") else Color.parseColor("#41516A"))
            }
            accentDot.background = GradientDrawable().apply {
                cornerRadius = 999f
                setColor(accentColor)
            }
            status.background = GradientDrawable().apply {
                cornerRadius = 999f
                setColor(if (item.statusLabel == "当前") Color.parseColor("#345D86") else Color.parseColor("#485A72"))
                setStroke(1f.dp(itemView.context).toInt(), if (hasFocus) accentColor else Color.parseColor("#8FA3B9"))
            }
            title.alpha = if (hasFocus) 1f else 0.92f
            iconBadge.alpha = if (hasFocus) 1f else 0.88f
            card.animate()
                .scaleX(if (hasFocus) 1.006f else 1f)
                .scaleY(if (hasFocus) 1.006f else 1f)
                .translationX(if (hasFocus) 4f else 0f)
                .setDuration(140L)
                .start()
            card.translationZ = if (hasFocus) 16f else 0f
        }

        private fun Float.dp(context: Context): Float {
            return this * context.resources.displayMetrics.density
        }
    }
}
