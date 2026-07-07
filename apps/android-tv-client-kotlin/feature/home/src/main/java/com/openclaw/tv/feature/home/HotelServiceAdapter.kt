package com.openclaw.tv.feature.home

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load

internal class HotelServiceAdapter : RecyclerView.Adapter<HotelServiceAdapter.HotelServiceViewHolder>() {

    private val items = mutableListOf<HotelServiceItem>()
    private var onItemClick: ((HotelServiceItem) -> Unit)? = null
    private var onItemFocus: ((Int, HotelServiceItem) -> Unit)? = null

    fun submitList(nextItems: List<HotelServiceItem>) {
        items.clear()
        items.addAll(nextItems)
        notifyDataSetChanged()
    }

    fun setOnItemClickListener(listener: (HotelServiceItem) -> Unit) {
        onItemClick = listener
    }

    fun setOnItemFocusListener(listener: (Int, HotelServiceItem) -> Unit) {
        onItemFocus = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HotelServiceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_hotel_service, parent, false)
        return HotelServiceViewHolder(view)
    }

    override fun onBindViewHolder(holder: HotelServiceViewHolder, position: Int) {
        holder.bind(
            item = items[position],
            onItemClick = onItemClick,
            onItemFocus = onItemFocus,
        )
    }

    override fun getItemCount(): Int = items.size

    internal class HotelServiceViewHolder(
        itemView: View,
    ) : RecyclerView.ViewHolder(itemView) {
        private val card = itemView.findViewById<FrameLayout>(R.id.hotel_service_card)
        private val image = itemView.findViewById<ImageView>(R.id.hotel_service_image)
        private val imageFallback = itemView.findViewById<TextView>(R.id.hotel_service_image_fallback)
        private val title = itemView.findViewById<TextView>(R.id.hotel_service_title)
        private val summary = itemView.findViewById<TextView>(R.id.hotel_service_summary)
        private val action = itemView.findViewById<TextView>(R.id.hotel_service_action)
        private var boundItem: HotelServiceItem? = null
        private var onItemFocus: ((Int, HotelServiceItem) -> Unit)? = null

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
            item: HotelServiceItem,
            onItemClick: ((HotelServiceItem) -> Unit)?,
            onItemFocus: ((Int, HotelServiceItem) -> Unit)?,
        ) {
            boundItem = item
            this.onItemFocus = onItemFocus
            title.text = item.title
            summary.text = item.summary
            action.text = item.actionLabel
            imageFallback.text = item.title.trim().take(1).ifBlank { "服" }
            if (item.imageUrl.isNotBlank()) {
                image.visibility = View.VISIBLE
                imageFallback.visibility = View.GONE
                image.load(item.imageUrl)
            } else {
                image.setImageDrawable(null)
                image.visibility = View.GONE
                imageFallback.visibility = View.VISIBLE
            }
            applyVisualState(item, card.hasFocus())
            card.setOnClickListener { onItemClick?.invoke(item) }
        }

        private fun applyVisualState(item: HotelServiceItem, hasFocus: Boolean) {
            val accentColor = parseColorOrDefault(item.accentColorHex, Color.parseColor("#56C596"))
            card.background = GradientDrawable().apply {
                cornerRadius = 14f.dp(itemView)
                setColor(if (hasFocus) blend(accentColor, "#151C25", 0.24f) else Color.parseColor("#1A2530"))
                setStroke(
                    if (hasFocus) 2f.dp(itemView).toInt() else 1f.dp(itemView).toInt(),
                    if (hasFocus) accentColor else Color.parseColor("#2A3642"),
                )
            }
            action.background = GradientDrawable().apply {
                cornerRadius = 999f
                setColor(if (hasFocus) withAlpha(accentColor, 0.22f) else Color.parseColor("#111A22"))
                setStroke(1f.dp(itemView).toInt(), withAlpha(accentColor, if (hasFocus) 0.72f else 0.36f))
            }
            card.animate()
                .scaleX(if (hasFocus) 1.025f else 1f)
                .scaleY(if (hasFocus) 1.025f else 1f)
                .translationY(if (hasFocus) -4f else 0f)
                .setDuration(140L)
                .start()
            card.translationZ = if (hasFocus) 14f else 0f
        }

        private fun parseColorOrDefault(value: String, fallback: Int): Int {
            return runCatching { Color.parseColor(value.trim()) }.getOrDefault(fallback)
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

        private fun blend(color: Int, baseHex: String, ratio: Float): Int {
            val base = Color.parseColor(baseHex)
            val clamped = ratio.coerceIn(0f, 1f)
            val inverse = 1f - clamped
            return Color.rgb(
                (Color.red(color) * clamped + Color.red(base) * inverse).toInt(),
                (Color.green(color) * clamped + Color.green(base) * inverse).toInt(),
                (Color.blue(color) * clamped + Color.blue(base) * inverse).toInt(),
            )
        }

        private fun Float.dp(view: View): Float {
            return this * view.context.resources.displayMetrics.density
        }
    }
}
