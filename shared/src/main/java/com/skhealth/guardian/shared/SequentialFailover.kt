package com.skhealth.guardian.shared

object SequentialFailover {
    fun <T> firstSuccessful(items: Iterable<T>, attempt: (T) -> Boolean): T? {
        for (item in items) {
            if (attempt(item)) return item
        }
        return null
    }
}
