package com.github.andreyasadchy.xtra.ui.settings

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.SettingsDragListItemBinding
import com.github.andreyasadchy.xtra.model.ui.SettingsDragListItem
import java.util.Collections

class SettingsDragListAdapter : ListAdapter<SettingsDragListItem, SettingsDragListAdapter.ViewHolder>(
    object : DiffUtil.ItemCallback<SettingsDragListItem>() {
        override fun areItemsTheSame(oldItem: SettingsDragListItem, newItem: SettingsDragListItem): Boolean {
            return oldItem.key == newItem.key
        }

        override fun areContentsTheSame(oldItem: SettingsDragListItem, newItem: SettingsDragListItem): Boolean {
            return true
        }
    }
) {
    var itemTouchHelper: ItemTouchHelper? = null
    var setDefault: ((SettingsDragListItem) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = SettingsDragListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: SettingsDragListItemBinding) : RecyclerView.ViewHolder(binding.root) {
        @SuppressLint("ClickableViewAccessibility")
        fun bind(item: SettingsDragListItem) {
            with(binding) {
                image.setOnTouchListener { view, event ->
                    if (event.action == MotionEvent.ACTION_DOWN) {
                        itemTouchHelper?.startDrag(this@ViewHolder)
                    }
                    false
                }
                text.text = item.text
                if (setDefault != null) {
                    setAsDefault.visibility = View.VISIBLE
                    setAsDefault.setOnClickListener {
                        setAsDefault.setImageResource(R.drawable.baseline_home_black_24)
                        setAsDefault.isClickable = false
                        setDefault!!(item)
                    }
                    if (item.default) {
                        setAsDefault.setImageResource(R.drawable.baseline_home_black_24)
                        setAsDefault.isClickable = false
                    } else {
                        setAsDefault.setImageResource(R.drawable.outline_home_black_24)
                        setAsDefault.isClickable = true
                    }
                } else {
                    setAsDefault.visibility = View.GONE
                }
                checkBox.visibility = View.VISIBLE
                checkBox.isChecked = item.enabled
                checkBox.setOnCheckedChangeListener { buttonView, isChecked ->
                    item.enabled = isChecked
                }
            }
        }
    }
}

/**
 * A fast drag can skip rows. Move one row at a time so the list order matches each
 * notifyItemMoved call; one swap of distant rows would scramble the rows in between.
 */
internal fun moveDragListItem(list: List<*>, from: Int, to: Int, notifyMoved: (Int, Int) -> Unit) {
    if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return
    val step = if (from < to) 1 else -1
    var position = from
    while (position != to) {
        Collections.swap(list, position, position + step)
        notifyMoved(position, position + step)
        position += step
    }
}
