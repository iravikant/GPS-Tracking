package com.gpstracking.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gpstracking.Room.TrackingSessionEntity
import com.gpstracking.databinding.ItemSessionBinding
import com.gpstracking.utils.DateTimeUtils
import com.gpstracking.utils.DistanceUtils

class SessionAdapter(
    private val onSessionClick: (TrackingSessionEntity) -> Unit
) : ListAdapter<TrackingSessionEntity, SessionAdapter.SessionViewHolder>(SessionDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
        val binding = ItemSessionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SessionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SessionViewHolder(
        private val binding: ItemSessionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(session: TrackingSessionEntity) {
            binding.apply {
                tvSessionTitle.text = "Session #${session.id}"
                tvStartTime.text = DateTimeUtils.formatDateTime(session.startTime)

                if (session.endTime != null) {
                    val duration = DistanceUtils.calculateDuration(session.startTime, session.endTime)
                    tvDuration.text = DistanceUtils.formatDuration(duration)
                    tvStatus.text = "Completed"
                } else {
                    tvDuration.text = "In Progress"
                    tvStatus.text = "Active"
                }

                root.setOnClickListener {
                    onSessionClick(session)
                }
            }
        }
    }

    private class SessionDiffCallback : DiffUtil.ItemCallback<TrackingSessionEntity>() {
        override fun areItemsTheSame(
            oldItem: TrackingSessionEntity,
            newItem: TrackingSessionEntity
        ): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(
            oldItem: TrackingSessionEntity,
            newItem: TrackingSessionEntity
        ): Boolean {
            return oldItem == newItem
        }
    }
}