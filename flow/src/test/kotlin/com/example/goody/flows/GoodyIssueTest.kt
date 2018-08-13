package com.example.goody.flows

import com.example.goody.BOG_NAME
import com.example.goody.assertFail
import com.example.goody.contracts.Candy
import com.example.goody.contracts.Goody
import net.corda.core.contracts.Amount
import net.corda.core.identity.Party
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class GoodyIssueTest {
    private companion object {
        private val log = loggerFor<GoodyIssueTest>()
        private val GOODY_REFERENCE = OpaqueBytes(byteArrayOf(0x65, 0x33, 0x00, 0x24, 0x7C, 0x69))
    }
    private lateinit var mockNet: MockNetwork
    private lateinit var bankOfGoodiesNode: StartedMockNode
    private lateinit var bankOfGoodies: Party
    private lateinit var notary: Party

    @Before
    fun start() {
        mockNet = MockNetwork(
            cordappPackages = listOf("com.example.goody.contracts", "com.example.goody.schemas")
        )
        bankOfGoodiesNode = mockNet.createPartyNode(BOG_NAME)
        bankOfGoodies = bankOfGoodiesNode.info.identityFromX500Name(BOG_NAME)
        notary = mockNet.defaultNotaryIdentity
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun `issue some goodies`() {
        val expected = Amount(1002, Candy("Toffee"))
        val future = bankOfGoodiesNode.startFlow(GoodyIssueFlow(expected, GOODY_REFERENCE, notary))
        mockNet.runNetwork()
        val issueTx = future.getOrThrow()
        log.info("Issue TX: {}", issueTx)

        val output = issueTx.tx.outputsOfType<Goody.State>().groupBy(Goody.State::owner)
        val bankGoodies = output[bankOfGoodies] ?: assertFail("Bank has received no goodies")
        assertEquals(1, bankGoodies.size)
        with(bankGoodies[0].amount) {
            assertEquals(expected.quantity, quantity)
            assertEquals(expected.token, token.product)
            assertEquals(bankOfGoodies, token.issuer.party)
            assertEquals(GOODY_REFERENCE, token.issuer.reference)
        }
    }
}
