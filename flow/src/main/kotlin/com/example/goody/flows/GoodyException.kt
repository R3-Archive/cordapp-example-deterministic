package com.example.goody.flows

import net.corda.core.flows.FlowException

open class GoodyException(message: String, cause: Throwable?) : FlowException(message, cause)
