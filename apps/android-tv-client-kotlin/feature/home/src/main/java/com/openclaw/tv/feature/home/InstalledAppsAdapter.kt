package com.openclaw.tv.feature.home

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

internal class InstalledAppsAdapter : RecyclerView.Adapter<InstalledAppsAdapter.InstalledAppViewHolder>() {

    private val items = mutableListOf<AppManagementItem>()
    private var onItemClick: ((AppManagementItem) -> Unit)? = null
    private var onItemDelete: ((AppManagementItem) -> Unit)? = null
    private var onItemFocus: ((Int, AppManagementItem) -> Unit)? = null
    private var iconResolver: InstalledAppIconResolver? = null

    fun submitList(nextItems: List<AppManagementItem>) {
        items.clear()
        items.addAll(nextItems)
        notifyDataSetChanged()
    }

    fun setOnItemClickListener(listener: (AppManagementItem) -> Unit) {
        onItemClick = listener
    }

    fun setOnItemDeleteListener(listener: (AppManagementItem) -> Unit) {
        onItemDelete = listener
    }

    fun setOnItemFocusListener(listener: (Int, AppManagementItem) -> Unit) {
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
            onItemDelete = onItemDelete,
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
        private val iconBadge = itemView.findViewById<TextView>(R.id.installed_app_icon_badge)
        private val title = itemView.findViewById<TextView>(R.id.installed_app_title)
        private val summary = itemView.findViewById<TextView>(R.id.installed_app_summary)
        private val hint = itemView.findViewById<TextView>(R.id.installed_app_hint)
        private var boundItem: AppManagementItem? = null
        private var onItemFocus: ((Int, AppManagementItem) -> Unit)? = null

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
            item: AppManagementItem,
            onItemClick: ((AppManagementItem) -> Unit)?,
            onItemDelete: ((AppManagementItem) -> Unit)?,
            onItemFocus: ((Int, AppManagementItem) -> Unit)?,
        ) {
            boundItem = item
            this.onItemFocus = onItemFocus
            bindIcon(item)
            title.text = item.title
            summary.text = item.summary
            metaLabel.text = item.metaLabel()
            typeChip.text = item.typeLabel
            hint.text = listOfNotNull(item.primaryActionLabel, item.secondaryActionLabel)
                .joinToString(" · ")
            applyVisualState(item, card.hasFocus())
            card.setOnClickListener { onItemClick?.invoke(item) }
            val canDelete = item.canDelete()
            card.setOnLongClickListener {
                if (!canDelete) {
                    return@setOnLongClickListener false
                }
                onItemDelete?.invoke(item)
                true
            }
            card.setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN || !canDelete) {
                    return@setOnKeyListener false
                }
                if (keyCode == KeyEvent.KEYCODE_MENU ||
                    keyCode == KeyEvent.KEYCODE_DEL ||
                    keyCode == KeyEvent.KEYCODE_FORWARD_DEL
                ) {
                    onItemDelete?.invoke(item)
                    return@setOnKeyListener true
                }
                false
            }
        }

        private fun bindIcon(item: AppManagementItem) {
            val installedIcon = when (item.kind) {
                AppManagementItemKind.INSTALLED,
                AppManagementItemKind.APK_UPGRADE,
                -> iconResolver.resolve(item.packageName)

                AppManagementItemKind.ADD_SHORTCUT,
                AppManagementItemKind.APK_INSTALL,
                -> null
            }
            if (installedIcon != null) {
                icon.setImageDrawable(installedIcon)
                icon.visibility = View.VISIBLE
                iconBadge.visibility = View.GONE
            } else {
                icon.setImageDrawable(null)
                icon.visibility = View.GONE
                iconBadge.text = item.monogram.ifBlank { "+" }
                iconBadge.visibility = View.VISIBLE
            }
        }

        private fun AppManagementItem.metaLabel(): String {
            return when (kind) {
                AppManagementItemKind.ADD_SHORTCUT -> "新增入口"
                AppManagementItemKind.INSTALLED -> if (isSystemApp) "系统入口" else "已安装应用"
                AppManagementItemKind.APK_INSTALL,
                AppManagementItemKind.APK_UPGRADE,
                -> "USB/本机 APK"
            }
        }

        private fun AppManagementItem.canDelete(): Boolean {
            return kind == AppManagementItemKind.INSTALLED &&
                !isSystemApp &&
                packageName.isNotBlank()
        }

        private fun applyVisualState(
            item: AppManagementItem,
            hasFocus: Boolean,
        ) {
            val accentColor = item.accentColor()
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

        private fun AppManagementItem.accentColor(): Int {
            return Color.parseColor(
                when (kind) {
                    AppManagementItemKind.ADD_SHORTCUT -> "#5FB8FF"
                    AppManagementItemKind.INSTALLED -> if (isSystemApp) "#6FA6FF" else "#FFAE61"
                    AppManagementItemKind.APK_INSTALL -> "#65D692"
                    AppManagementItemKind.APK_UPGRADE -> "#F0B868"
                },
            )
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
