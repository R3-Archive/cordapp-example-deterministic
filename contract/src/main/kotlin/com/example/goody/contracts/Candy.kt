package com.example.goody.contracts

import com.fasterxml.jackson.annotation.JsonProperty
import net.corda.core.serialization.CordaSerializable

@CordaSerializable
class Candy(@JsonProperty("type") type: String) {
    val type: String = type.toUpperCase()
    override fun toString(): String = type

    override fun hashCode(): Int = type.hashCode()
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || other.javaClass != javaClass) {
            return false
        }
        other as Candy
        return type == other.type
    }
}
