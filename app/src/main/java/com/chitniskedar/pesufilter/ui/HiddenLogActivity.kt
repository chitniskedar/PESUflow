package com.chitniskedar.pesufilter.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chitniskedar.pesufilter.R
import com.chitniskedar.pesufilter.data.NotificationItem
import com.chitniskedar.pesufilter.databinding.ActivityHiddenLogBinding
import com.chitniskedar.pesufilter.databinding.ItemHiddenNotificationBinding
import com.chitniskedar.pesufilter.utils.PreferencesManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HiddenLogActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHiddenLogBinding
    private lateinit var preferencesManager: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHiddenLogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferencesManager = PreferencesManager(this)
        binding.recyclerHiddenLog.layoutManager = LinearLayoutManager(this)
        binding.recyclerHiddenLog.adapter = HiddenLogAdapter(preferencesManager.getSavedNotifications())
    }

    private class HiddenLogAdapter(
        private val items: List<NotificationItem>
    ) : RecyclerView.Adapter<HiddenLogAdapter.HiddenLogViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HiddenLogViewHolder {
            val binding = ItemHiddenNotificationBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return HiddenLogViewHolder(binding)
        }

        override fun onBindViewHolder(holder: HiddenLogViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        class HiddenLogViewHolder(
            private val binding: ItemHiddenNotificationBinding
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(item: NotificationItem) {
                binding.textMessage.text = item.text
                binding.textCategory.text = item.category
                binding.textTimestamp.text = DATE_FORMAT.format(Date(item.timestamp))
                binding.textShownStatus.text = if (item.isShown) {
                    binding.root.context.getString(R.string.status_shown)
                } else {
                    binding.root.context.getString(R.string.status_hidden)
                }
            }

            companion object {
                private val DATE_FORMAT =
                    SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
            }
        }
    }
}
