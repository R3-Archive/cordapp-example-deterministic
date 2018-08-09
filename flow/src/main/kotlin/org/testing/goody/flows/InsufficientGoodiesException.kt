package org.testing.goody.flows

class InsufficientGoodiesException(message: String, cause: Throwable?) : GoodyException(message, cause) {
    constructor(message: String) : this(message, null)
}
