package com.renhard.caloriesestimator.module.main.adapter

import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.renhard.caloriesestimator.databinding.CalorieRowBinding
import com.renhard.caloriesestimator.module.camera.model.CaloriesTableModel
import com.renhard.caloriesestimator.module.main.model.CaloriePredictModel

class MainAdapter : PagingDataAdapter<CaloriePredictModel, RecyclerView.ViewHolder>(DIFF_CALLBACK) {
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
        val viewHolder = ItemViewHolder(itemHomeBinding)
        return viewHolder
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val holder = holder as ItemViewHolder
        val item = getItem(position)
        if (item != null) {
            holder.bind(item)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return VIEW_ITEM
    }

    override fun getItemCount(): Int {
        return snapshot().items.size
    }

    class ItemViewHolder(private val binding: CalorieRowBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: CaloriePredictModel) {
            with(binding) {
                val context = tvFoodName.context
                tvFoodName.text = item.foodName
                tvCalorie.text = "${item.calorie} kkal/g"

                Glide.with(context)
                    .load(CaloriesTableModel()
                    .getIconByClass(item.foodName))
                    .circleCrop()
                    .into(ivPicture)
            }
        }
    }
}