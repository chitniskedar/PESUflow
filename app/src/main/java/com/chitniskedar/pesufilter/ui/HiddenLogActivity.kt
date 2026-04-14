package com.chitniskedar.pesufilter.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chitniskedar.pesufilter.R
import com.chitniskedar.pesufilter.databinding.ActivityHiddenLogBinding
import com.chitniskedar.pesufilter.databinding.ItemHiddenNotificationBinding
import com.chitniskedar.pesufilter.model.Announcement
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
        binding.recyclerHiddenLog.adapter = HistoryAdapter(preferencesManager.getSavedAnnouncements())
    }

    private class HistoryAdapter(
        private val items: List<Announcement>
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
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        class HistoryViewHolder(
            private val binding: ItemHiddenNotificationBinding
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(item: Announcement) {
                binding.textMessage.text = item.title
                binding.textCategory.text = item.date
                binding.textTimestamp.text = item.link ?: binding.root.context.getString(R.string.no_attachment_link)
                binding.textShownStatus.text = binding.root.context.getString(R.string.saved_locally)
            }
        }
    }
}
