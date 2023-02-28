package com.ch4vi.meetingsignal.utils

interface Mapper<T, S> {
    fun map(dto: T): S
}
