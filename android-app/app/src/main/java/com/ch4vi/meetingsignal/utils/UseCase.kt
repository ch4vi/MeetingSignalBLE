package com.ch4vi.meetingsignal.utils

import kotlinx.coroutines.flow.Flow

typealias UseCase<IN, OUT> = (IN) -> Flow<OUT>

typealias Refresh = Boolean