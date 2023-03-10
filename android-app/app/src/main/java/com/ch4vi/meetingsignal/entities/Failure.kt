package com.ch4vi.meetingsignal.entities

sealed class Failure(msg: String? = null) : Throwable(msg) {
    class PermissionFailure(msg: String) : Failure(msg)
    class ConnectionFailure(msg: String) : Failure(msg)
    class UnexpectedFailure(msg: String? = null) : Failure(msg)
}