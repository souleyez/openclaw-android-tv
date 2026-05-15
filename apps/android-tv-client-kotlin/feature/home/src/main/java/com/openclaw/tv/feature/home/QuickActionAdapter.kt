package com.openclaw.tv.feature.home

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

internal class QuickActionAdapter : RecyclerView.Adapter<QuickActionAdapter.QuickActionViewHolder>() {

    private val items = mutableListOf<QuickActionItem>()
    private var onItemClick: ((QuickActionItem) -> Unit)? = null
    private var onItemFocus: ((Int, QuickActionItem) -> Unit)? = null
    private var onItemNavigateUp: ((Int, QuickActionItem) -> Boolean)? = null

    fun submitList(nextItems: List<QuickActionItem>) {
        items.clear()
        items.addAll(nextItems)
        notifyDataSetChanged()
    }

    fun setOnItemClickListener(listener: (QuickActionItem) -> Unit) {
        onItemClick = listener
    }

    fun setOnItemFocusListener(listener: (Int, QuickActionItem) -> Unit) {
        onItemFocus = listener
    }

    fun setOnItemNavigateUpListener(listener: (Int, QuickActionItem) -> Boolean) {
        onItemNavigateUp = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuickActionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_quick_action, parent, false)
        return QuickActionViewHolder(view)
    }

    override fun onBindViewHolder(holder: QuickActionViewHolder, position: Int) {
        holder.bind(
            item = items[position],
            onItemClick = onItemClick,
            onItemFocus = onItemFocus,
            onItemNavigateUp = onItemNavigateUp,
        )
    }

    override fun getItemCount(): Int = items.size

    internal class QuickActionViewHolder(
        itemView: View,
    ) : RecyclerView.ViewHolder(itemView) {
        private val card = itemView.findViewById<FrameLayout>(R.id.quick_action_card)
        private val topPanel = itemView.findViewById<FrameLayout>(R.id.quick_action_top_panel)
        private val surface = itemView.findViewById<View>(R.id.quick_action_surface)
        private val iconPlate = itemView.findViewById<FrameLayout>(R.id.quick_action_icon_plate)
        private val accent = itemView.findViewById<View>(R.id.quick_action_accent)
        private val iconBadge = itemView.findViewById<TextView>(R.id.quick_action_icon_badge)
        private val overline = itemView.findViewById<TextView>(R.id.quick_action_overline)
        private val title = itemView.findViewById<TextView>(R.id.quick_action_title)
        private val summary = itemView.findViewById<TextView>(R.id.quick_action_summary)
        private val action = itemView.findViewById<TextView>(R.id.quick_action_hint)
        private var boundItem: QuickActionItem? = null
        private var onItemFocus: ((Int, QuickActionItem) -> Unit)? = null
        private var onItemNavigateUp: ((Int, QuickActionItem) -> Boolean)? = null

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
            card.setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN || keyCode != KeyEvent.KEYCODE_DPAD_UP) {
                    return@setOnKeyListener false
                }
                val item = boundItem ?: return@setOnKeyListener false
                val adapterPosition = bindingAdapterPosition
                if (adapterPosition == RecyclerView.NO_POSITION) {
                    return@setOnKeyListener false
                }
                onItemNavigateUp?.invoke(adapterPosition, item) == true
            }
        }

        fun bind(
            item: QuickActionItem,
            onItemClick: ((QuickActionItem) -> Unit)?,
            onItemFocus: ((Int, QuickActionItem) -> Unit)?,
            onItemNavigateUp: ((Int, QuickActionItem) -> Boolean)?,
        ) {
            boundItem = item
            this.onItemFocus = onItemFocus
            this.onItemNavigateUp = onItemNavigateUp
            iconBadge.text = resolveIconBadge(item.id)
            overline.text = resolveOverline(item.id)
            title.text = item.title
            summary.text = item.summary
            action.text = item.actionLabel
            overline.visibility = View.GONE
            summary.visibility = if (item.id == HomeViewModel.QUICK_ACTION_CAST && item.summary.isNotBlank()) {
                View.VISIBLE
            } else {
                View.GONE
            }
            action.visibility = View.GONE
            applyVisualState(item, card.hasFocus())
            card.setOnClickListener { onItemClick?.invoke(item) }
        }

        private fun applyVisualState(
            item: QuickActionItem,
            hasFocus: Boolean,
        ) {
            val accentColor = Color.parseColor(item.accentColorHex)
            accent.background = GradientDrawable().apply {
                cornerRadius = 999f
                setColor(accentColor)
            }
            topPanel.background = buildTopPanelBackground(accentColor, hasFocus)
            surface.background = buildTopPanelScrim(accentColor, hasFocus)
            iconPlate.background = buildIconPlateBackground(accentColor, hasFocus)
            action.background = buildActionPillBackground(accentColor, hasFocus)
            card.background = GradientDrawable().apply {
                cornerRadius = 18f.dp(itemView.context)
                setColor(blend(accentColor, "#151C25", if (hasFocus) 0.18f else 0.11f))
                setStroke(
                    if (hasFocus) 2f.dp(itemView.context).toInt() else 1f.dp(itemView.context).toInt(),
                    if (hasFocus) accentColor else Color.parseColor("#2A3642"),
                )
            }
            card.animate()
                .scaleX(if (hasFocus) 1.035f else 1f)
                .scaleY(if (hasFocus) 1.035f else 1f)
                .translationY(if (hasFocus) -6f else 0f)
                .setDuration(140L)
                .start()
            card.translationZ = if (hasFocus) 18f else 0f
        }

        private fun buildTopPanelBackground(accentColor: Int, hasFocus: Boolean): GradientDrawable {
            return GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    withAlpha(accentColor, if (hasFocus) 0.30f else 0.22f),
                    blend(accentColor, "#18212B", if (hasFocus) 0.16f else 0.10f),
                ),
            ).apply {
                cornerRadius = 18f.dp(itemView.context)
            }
        }

        private fun buildTopPanelScrim(accentColor: Int, hasFocus: Boolean): GradientDrawable {
            return GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(withAlpha(accentColor, if (hasFocus) 0.18f else 0.10f), Color.TRANSPARENT),
            ).apply {
                cornerRadius = 18f.dp(itemView.context)
            }
        }

        private fun buildIconPlateBackground(accentColor: Int, hasFocus: Boolean): GradientDrawable {
            return GradientDrawable().apply {
                cornerRadius = 18f.dp(itemView.context)
                setColor(withAlpha(accentColor, if (hasFocus) 0.26f else 0.16f))
                setStroke(1f.dp(itemView.context).toInt(), withAlpha(Color.WHITE, 0.14f))
            }
        }

        private fun buildActionPillBackground(accentColor: Int, hasFocus: Boolean): GradientDrawable {
            return GradientDrawable().apply {
                cornerRadius = 999f
                setColor(if (hasFocus) withAlpha(accentColor, 0.22f) else Color.parseColor("#1B232C"))
                setStroke(
                    1f.dp(itemView.context).toInt(),
                    if (hasFocus) withAlpha(accentColor, 0.72f) else withAlpha(Color.WHITE, 0.12f),
                )
            }
        }

        private fun resolveIconBadge(id: String): String {
            return when (id) {
                HomeViewModel.QUICK_ACTION_LOCAL -> "USB"
                HomeViewModel.QUICK_ACTION_CAST -> "TV"
                HomeViewModel.QUICK_ACTION_SETTINGS -> "SYS"
                HomeViewModel.QUICK_ACTION_FREE_PLAY -> "GO"
                HomeViewModel.QUICK_ACTION_LOCAL_APPS -> "APP"
                else -> "GO"
            }
        }

        private fun resolveOverline(id: String): String {
            return when (id) {
                HomeViewModel.QUICK_ACTION_LOCAL -> "本机内容"
                HomeViewModel.QUICK_ACTION_CAST -> "无线连接"
                HomeViewModel.QUICK_ACTION_SETTINGS -> "系统控制"
                HomeViewModel.QUICK_ACTION_FREE_PLAY -> "快速进入"
                HomeViewModel.QUICK_ACTION_LOCAL_APPS -> "应用清单"
                else -> "快捷入口"
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
