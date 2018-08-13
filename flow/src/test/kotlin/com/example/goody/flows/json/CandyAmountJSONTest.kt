package com.example.goody.flows.json

import com.example.goody.contracts.Candy
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.client.jackson.internal.CordaModule
import net.corda.core.contracts.Amount
import org.assertj.core.api.Assertions.*
import org.junit.Test

class CandyAmountJSONTest {
    private companion object {
        private val mapper: ObjectMapper = ObjectMapper().registerModule(CordaModule())
        private val NOUGAT = Candy("Nougat")
    }

    @Test
    fun testDeserialize() {
        val str = """{
            "quantity": 100,
            "token": {
                "type": "Nougat"
            }
}
""".trimIndent()

        val candy: Amount<Candy> = mapper.readValue(str, object : TypeReference<Amount<Candy>>() {})
        assertThat(candy.quantity).isEqualTo(100)
        assertThat(candy.token).isEqualTo(NOUGAT)
    }

    @Test
    fun testSerialize() {
        val candy = Amount(12345, NOUGAT)
        assertThat(mapper.writeValueAsString(candy)).isEqualTo(""""12345 Nougat"""")
    }
}
