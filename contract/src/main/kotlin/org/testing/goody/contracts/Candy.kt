package org.testing.goody.contracts

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class Candy(val type: String)
