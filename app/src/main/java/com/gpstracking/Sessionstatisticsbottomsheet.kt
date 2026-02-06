package com.gpstracking

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.gpstracking.databinding.BottomSheetStatisticsBinding
import com.gpstracking.utils.DateTimeUtils
import com.gpstracking.utils.DistanceUtils

class SessionStatisticsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetStatisticsBinding? = null
    private val binding get() = _binding!!

    private var distance: Float = 0f
    private var duration: Long = 0L
    private var pointsCount: Int = 0
    private var startTime: Long = 0L
    private var endTime: Long? = null

    companion object {
        private const val ARG_DISTANCE = "distance"
        private const val ARG_DURATION = "duration"
        private const val ARG_POINTS_COUNT = "points_count"
        private const val ARG_START_TIME = "start_time"
        private const val ARG_END_TIME = "end_time"

        fun newInstance(
            distance: Float,
            duration: Long,
            pointsCount: Int,
            startTime: Long,
            endTime: Long?
        ): SessionStatisticsBottomSheet {
            return SessionStatisticsBottomSheet().apply {
                arguments = Bundle().apply {
                    putFloat(ARG_DISTANCE, distance)
                    putLong(ARG_DURATION, duration)
                    putInt(ARG_POINTS_COUNT, pointsCount)
                    putLong(ARG_START_TIME, startTime)
                    endTime?.let { putLong(ARG_END_TIME, it) }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            distance = it.getFloat(ARG_DISTANCE)
            duration = it.getLong(ARG_DURATION)
            pointsCount = it.getInt(ARG_POINTS_COUNT)
            startTime = it.getLong(ARG_START_TIME)
            endTime = if (it.containsKey(ARG_END_TIME)) it.getLong(ARG_END_TIME) else null
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetStatisticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViews()
        updateStatistics()
    }

    private fun setupViews() {
        binding.btnClose.setOnClickListener {
            dismiss()
        }
    }

    private fun updateStatistics() {
        binding.tvDistance.text = DistanceUtils.formatDistance(distance)

        binding.tvDuration.text = DistanceUtils.formatDuration(duration)

        binding.tvPointsCount.text = "$pointsCount points"

        binding.tvStartTime.text = DateTimeUtils.formatDateTime(startTime)

        binding.tvEndTime.text = endTime?.let { DateTimeUtils.formatDateTime(it) } ?: "In Progress"
    }

    fun updateData(
        distance: Float,
        duration: Long,
        pointsCount: Int,
        startTime: Long,
        endTime: Long?
    ) {
        this.distance = distance
        this.duration = duration
        this.pointsCount = pointsCount
        this.startTime = startTime
        this.endTime = endTime

        if (_binding != null) {
            updateStatistics()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}