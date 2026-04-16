package com.openclaw.tv.feature.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppRailAdapter : RecyclerView.Adapter<AppRailAdapter.AppViewHolder>() {

    private val items = mutableListOf<FeaturedAppItem>()

    fun submitList(nextItems: List<FeaturedAppItem>) {
        items.clear()
        items.addAll(nextItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_rail, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val card = itemView.findViewById<LinearLayout>(R.id.app_card)
        private val title = itemView.findViewById<TextView>(R.id.app_title)
        private val summary = itemView.findViewById<TextView>(R.id.app_summary)

        fun bind(item: FeaturedAppItem) {
            title.text = item.title
            summary.text = item.summary
            card.alpha = if (item.installed) 1.0f else 0.78f
        }
    }
}
