package com.example.goody.api

import com.example.goody.contracts.Candy
import com.fasterxml.jackson.annotation.JsonProperty
import net.corda.core.contracts.Amount
import net.corda.core.identity.Party

class TransferRequest(
    @JsonProperty("candy")
    val candy: Amount<Candy>,

    @JsonProperty("recipient")
    val recipient: Party
)
