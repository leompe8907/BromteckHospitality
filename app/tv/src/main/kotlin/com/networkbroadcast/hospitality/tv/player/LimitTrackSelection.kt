package com.networkbroadcast.hospitality.tv.player

import androidx.media3.common.Format
import androidx.media3.common.TrackGroup
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.upstream.BandwidthMeter
import com.google.common.collect.ImmutableList
import kotlin.math.min

@UnstableApi
class LimitTrackSelection @JvmOverloads constructor(
    group: TrackGroup,
    tracks: IntArray,
    type: Int,
    bandwidthMeter: BandwidthMeter,
    minDurationForQualityIncreaseMs: Long = DEFAULT_MIN_DURATION_FOR_QUALITY_INCREASE_MS.toLong(),
    maxDurationForQualityDecreaseMs: Long = DEFAULT_MAX_DURATION_FOR_QUALITY_DECREASE_MS.toLong(),
    minDurationToRetainAfterDiscardMs: Long = DEFAULT_MIN_DURATION_TO_RETAIN_AFTER_DISCARD_MS.toLong(),
    maxWidthToDiscard: Int,
    maxHeightToDiscard: Int,
    bandwidthFraction: Float = DEFAULT_BANDWIDTH_FRACTION,
    bufferedFractionToLiveEdgeForQualityIncrease: Float = DEFAULT_BUFFERED_FRACTION_TO_LIVE_EDGE_FOR_QUALITY_INCREASE,
    adaptationCheckpoints: ImmutableList<AdaptationCheckpoint>,
    clock: Clock = Clock.DEFAULT,
    private val bitrateLimitter: BitrateLimitter
) : AdaptiveTrackSelection(
    group,
    tracks,
    type,
    bandwidthMeter,
    minDurationForQualityIncreaseMs,
    maxDurationForQualityDecreaseMs,
    minDurationToRetainAfterDiscardMs,
    maxWidthToDiscard,
    maxHeightToDiscard,
    bandwidthFraction,
    bufferedFractionToLiveEdgeForQualityIncrease,
    adaptationCheckpoints,
    clock
) {

    class Factory @JvmOverloads constructor(
        private val minDurationForQualityIncreaseMs: Int = DEFAULT_MIN_DURATION_FOR_QUALITY_INCREASE_MS,
        private val maxDurationForQualityDecreaseMs: Int = DEFAULT_MAX_DURATION_FOR_QUALITY_DECREASE_MS,
        private val minDurationToRetainAfterDiscardMs: Int = DEFAULT_MIN_DURATION_TO_RETAIN_AFTER_DISCARD_MS,
        private val maxWidthToDiscard: Int = 0,
        private val maxHeightToDiscard: Int = 0,
        private val bandwidthFraction: Float = DEFAULT_BANDWIDTH_FRACTION,
        private val bufferedFractionToLiveEdgeForQualityIncrease: Float = DEFAULT_BUFFERED_FRACTION_TO_LIVE_EDGE_FOR_QUALITY_INCREASE,
        private val clock: Clock = Clock.DEFAULT,
        private val bitrateLimitter: BitrateLimitter
    ) : AdaptiveTrackSelection.Factory(
        minDurationForQualityIncreaseMs,
        maxDurationForQualityDecreaseMs,
        minDurationToRetainAfterDiscardMs,
        bandwidthFraction,
        bufferedFractionToLiveEdgeForQualityIncrease,
        clock
    ) {

        override fun createAdaptiveTrackSelection(
            group: TrackGroup,
            tracks: IntArray,
            type: Int,
            bandwidthMeter: BandwidthMeter,
            adaptationCheckpoints: ImmutableList<AdaptationCheckpoint>
        ): AdaptiveTrackSelection {
            return LimitTrackSelection(
                group,
                tracks,
                type,
                bandwidthMeter,
                minDurationForQualityIncreaseMs.toLong(),
                maxDurationForQualityDecreaseMs.toLong(),
                minDurationToRetainAfterDiscardMs.toLong(),
                maxWidthToDiscard,
                maxHeightToDiscard,
                bandwidthFraction,
                bufferedFractionToLiveEdgeForQualityIncrease,
                adaptationCheckpoints,
                clock,
                bitrateLimitter
            )
        }
    }

    override fun canSelectFormat(
        format: Format,
        trackBitrate: Int,
        effectiveBitrate: Long
    ): Boolean {
        val limitedEffectiveBitrate = min(bitrateLimitter.getMaxBitrate(), effectiveBitrate)

        //LogHelper.logInfoDebugMessage("BitrateLimitter", "called canSelectFormat format: " + format + ", trackBitrate: " + trackBitrate + " bitrateLimitter: " + bitrateLimitter.getMaxBitrate() + " effectiveBitrate: " + effectiveBitrate + " result: " + super.canSelectFormat(format, trackBitrate, limitedEffectiveBitrate))
        return super.canSelectFormat(format, trackBitrate, limitedEffectiveBitrate)
    }
}