package com.example.goody.contracts

import com.fasterxml.jackson.annotation.JsonProperty
import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class Candy(@JsonProperty("type") val type: String) {
    override fun toString(): String = type
}
