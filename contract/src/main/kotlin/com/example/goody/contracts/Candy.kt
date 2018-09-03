package com.example.goody.contracts

import com.fasterxml.jackson.annotation.JsonProperty
import net.corda.core.contracts.Amount
import net.corda.core.contracts.Issued
import net.corda.core.contracts.PartyAndReference
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

infix fun Amount<Candy>.issuedBy(deposit: PartyAndReference) = Amount(quantity, displayTokenSize, token.issuedBy(deposit))
infix fun Candy.issuedBy(deposit: PartyAndReference) = Issued(deposit, this)

operator fun Amount<Candy>.plus(extra: Long): Amount<Candy> = Amount(quantity + extra, token)
operator fun Amount<Candy>.minus(less: Long): Amount<Candy> = Amount(quantity - less, token)
operator fun Amount<Candy>.div(divisor: Long): Amount<Candy> = Amount(quantity / divisor, token)