package com.example.goody.api

import com.example.goody.contracts.Candy
import com.fasterxml.jackson.annotation.JsonProperty
import net.corda.core.contracts.Amount
import net.corda.core.identity.Party
import net.corda.core.utilities.OpaqueBytes

class IssueRequest(
    @JsonProperty("candy")
    val candy: Amount<Candy>,

    @JsonProperty("issuerReference")
    val issuerReference: OpaqueBytes,

    @JsonProperty("notary")
    val notary: Party?
)
