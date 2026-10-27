package com.networkbroadcast.hospitality.tv.player

class BitrateLimitter {

    private var maxBitrate = Long.MAX_VALUE

    fun limitMaxBitrate(bitrate: Long) {
        android.util.Log.i("Hospitality.Player", "limitMaxBitrate($bitrate)")
        this.maxBitrate = bitrate
    }

    fun getMaxBitrate(): Long {
        return maxBitrate
    }
}