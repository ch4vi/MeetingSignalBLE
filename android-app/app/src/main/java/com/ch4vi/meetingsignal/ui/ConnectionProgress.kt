package com.ch4vi.meetingsignal.ui

private const val PER_CENT = 100

enum class ConnectionProgress {
    DISCONNECTED,
    SCANNING,
    DEVICE_FOUND,
    CONNECTING,
    CONNECTED,
    ;
}

fun ConnectionProgress.getProgress(): Int {
    if (this == ConnectionProgress.DISCONNECTED) return PER_CENT

    val totalSteps = ConnectionProgress.values().size - 1 // DISCONNECTED is not a step
    return this.ordinal.times(PER_CENT).div(totalSteps)
}
