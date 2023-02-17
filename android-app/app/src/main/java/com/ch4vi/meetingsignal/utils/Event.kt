package com.ch4vi.meetingsignal.utils

import androidx.lifecycle.Observer

class Event<out T>(private val content: T) {

    private var isUsed = false

    fun get(): T? {
        return if (isUsed) null
        else {
            isUsed = true
            content
        }
    }

    fun forceGet(): T = content
}

class EventObserver<T>(private val onEventUnhandledContent: (T) -> Unit) : Observer<Event<T>> {
    override fun onChanged(value: Event<T>) {
        value.get()?.let { v ->
            onEventUnhandledContent(v)
        }
    }
}

fun <T> T.toEvent() = Event(this)
