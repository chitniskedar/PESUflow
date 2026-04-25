package com.chitniskedar.pesufilter.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chitniskedar.pesufilter.R
import com.chitniskedar.pesufilter.databinding.ActivityHiddenLogBinding
import com.chitniskedar.pesufilter.databinding.ItemHiddenNotificationBinding
import com.chitniskedar.pesufilter.model.Announcement
import com.chitniskedar.pesufilter.utils.FilterManager
import com.chitniskedar.pesufilter.utils.PreferencesManager

class HiddenLogActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHiddenLogBinding
    private lateinit var preferencesManager: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHiddenLogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferencesManager = PreferencesManager(this)
        binding.recyclerHiddenLog.layoutManager = LinearLayoutManager(this)
        binding.recyclerHiddenLog.adapter = HistoryAdapter(
            items = preferencesManager.getSavedAnnouncements(),
            filterManager = FilterManager(preferencesManager)
        )
    }

    private class HistoryAdapter(
        private val items: List<Announcement>,
        private val filterManager: FilterManager
    ) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
            val binding = ItemHiddenNotificationBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return HistoryViewHolder(binding)
        }

        override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
            holder.bind(items[position], filterManager)
        }

        override fun getItemCount(): Int = items.size

        class HistoryViewHolder(
            private val binding: ItemHiddenNotificationBinding
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(item: Announcement, filterManager: FilterManager) {
                val context = binding.root.context
                val category = filterManager.categorize(item.fullText)

                binding.textMessage.text = item.title
                binding.textCategory.text = category
                binding.textTimestamp.text = item.fullText
                binding.textShownStatus.text = context.getString(
                    R.string.history_meta,
                    item.date.ifBlank { context.getString(R.string.saved_locally) },
                    context.getString(R.string.saved_locally)
                )
                binding.viewAccent.setBackgroundColor(
                    ContextCompat.getColor(
                        context,
                        when (category) {
                            PreferencesManager.CATEGORY_EXAM -> R.color.navy_accent
                            PreferencesManager.CATEGORY_INTERNSHIP -> R.color.amber_tint
                            PreferencesManager.CATEGORY_NOTICE -> R.color.sky_tint
                            else -> R.color.red_tint
                        }
                    )
                )
                binding.textCategory.setTextColor(
                    ContextCompat.getColor(
                        context,
                        when (category) {
                            PreferencesManager.CATEGORY_EXAM -> R.color.sky_tint
                            PreferencesManager.CATEGORY_INTERNSHIP -> R.color.amber_tint
                            PreferencesManager.CATEGORY_NOTICE -> R.color.sky_tint
                            else -> R.color.red_tint
                        }
                    )
                )
            }
        }
    }
}
