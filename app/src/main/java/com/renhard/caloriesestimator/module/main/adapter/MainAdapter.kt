package com.renhard.caloriesestimator.module.main.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.renhard.caloriesestimator.databinding.CalorieRowBinding
import com.renhard.caloriesestimator.module.camera.model.CaloriesTableModel
import com.renhard.caloriesestimator.module.main.model.CaloriePredictModel

class MainAdapter(private val callback: MainAdapterCallback) : PagingDataAdapter<CaloriePredictModel, RecyclerView.ViewHolder>(DIFF_CALLBACK) {
    private val VIEW_ITEM = 0

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<CaloriePredictModel>() {
            override fun areItemsTheSame(oldItem: CaloriePredictModel, newItem: CaloriePredictModel): Boolean {
                return oldItem.foodName == newItem.foodName
            }

            override fun areContentsTheSame(oldItem: CaloriePredictModel, newItem: CaloriePredictModel): Boolean {
                return oldItem.foodName == newItem.foodName
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val itemHomeBinding = CalorieRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        val viewHolder = ItemViewHolder(itemHomeBinding, callback)
        return viewHolder
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val holder = holder as ItemViewHolder
        val item = getItem(position)
        if (item != null) {
            holder.bind(item, position)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return VIEW_ITEM
    }

    override fun getItemCount(): Int {
        return snapshot().items.size
    }

    class ItemViewHolder(private val binding: CalorieRowBinding, private val callback: MainAdapterCallback) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: CaloriePredictModel, position: Int) {
            with(binding) {
                val context = tvFoodName.context
                tvFoodName.text = item.foodName
                val totalCalorie = item.calorie.toFloat() * item.weight / 1000f
                val totalCalorieFormatted = String.format("%.2f", totalCalorie)
                val weightFormatted = String.format("%.2f", item.weight)
                tvCalorie.text = "${weightFormatted} g • $totalCalorieFormatted kkal"

                Glide.with(context)
                    .load(CaloriesTableModel()
                    .getIconByClass(item.foodName))
                    .circleCrop()
                    .into(ivPicture)

                btnEdit.setOnClickListener {
                    callback.onEditWeight(position)
                }
            }
        }
    }
}

interface MainAdapterCallback {
    fun onEditWeight(position: Int)
}