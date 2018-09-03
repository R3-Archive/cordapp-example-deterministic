package com.example.goody.flows

import com.example.goody.BOG_NAME
import com.example.goody.SOG_NAME
import com.example.goody.assertFail
import com.example.goody.contracts.Candy
import com.example.goody.contracts.Goody
import net.corda.core.contracts.Amount
import net.corda.core.identity.Party
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class GoodyMultiIssuerTest {
    private companion object {
        private val log = loggerFor<GoodyMultiIssuerTest>()
        private const val CANDY_TYPE = "Nougat"
        private val bankCandy = Amount(7684, Candy(CANDY_TYPE))
        private val shopCandy = Amount(963, Candy(CANDY_TYPE))
        private val GOODY_BANK_REFERENCE = OpaqueBytes(byteArrayOf(0x65, 0x33, 0x00, 0x24, 0x7C, 0x69))
        private val GOODY_SHOP_REFERENCE = OpaqueBytes(byteArrayOf(0x7E, 0x50, 0x3F, 0x18, 0x0E, 0x11))
    }

    private lateinit var mockNet: MockNetwork
    private lateinit var bankOfGoodiesNode: StartedMockNode
    private lateinit var shopOfGoodiesNode: StartedMockNode
    private lateinit var bankOfGoodies: Party
    private lateinit var shopOfGoodies: Party
    private lateinit var aliceNode: StartedMockNode
    private lateinit var alice: Party
    private lateinit var bob: Party

    @Before
    fun start() {
        mockNet = MockNetwork(
            cordappPackages = listOf("com.example.goody.contracts", "com.example.goody.schemas")
        )
        bankOfGoodiesNode = mockNet.createPartyNode(BOG_NAME)
        bankOfGoodies = bankOfGoodiesNode.info.identityFromX500Name(BOG_NAME)
        shopOfGoodiesNode = mockNet.createPartyNode(SOG_NAME)
        shopOfGoodies = shopOfGoodiesNode.info.identityFromX500Name(SOG_NAME)
        aliceNode = mockNet.createPartyNode(ALICE_NAME)
        alice = aliceNode.info.singleIdentity()
        bob = mockNet.createPartyNode(BOB_NAME).info.singleIdentity()

        val bankFuture = bankOfGoodiesNode.startFlow(GoodyIssueFlow(bankCandy, GOODY_BANK_REFERENCE, mockNet.defaultNotaryIdentity))
        bankFuture.getOrThrow()
        val shopFuture = shopOfGoodiesNode.startFlow(GoodyIssueFlow(shopCandy, GOODY_SHOP_REFERENCE, mockNet.defaultNotaryIdentity))
        shopFuture.getOrThrow()
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun `check transfer candy from all issuers`() {
        val fromBankFuture = bankOfGoodiesNode.startFlow(GoodyTransferFlow(bankCandy, alice))
        val fromShopFuture = shopOfGoodiesNode.startFlow(GoodyTransferFlow(shopCandy, alice))
        mockNet.runNetwork()
        val bankTransfer = fromBankFuture.getOrThrow().tx.outputsOfType<Goody.State>()[0]
        log.info("From Bank To Alice TX: output={}", bankTransfer)
        val shopTransfer = fromShopFuture.getOrThrow().tx.outputsOfType<Goody.State>()[0]
        log.info("From Shop To Alice TX: output={}", shopTransfer)

        val toBobFuture = aliceNode.startFlow(GoodyTransferFlow(bankCandy + shopCandy, bob))
        mockNet.runNetwork()
        val toBobGoodies = toBobFuture.getOrThrow().tx.outputsOfType<Goody.State>().groupBy(Goody.State::owner)

        log.info("From Alice To Bob: output={}", toBobGoodies)
        assertEquals(1, toBobGoodies.size)

        val bobGoodies = toBobGoodies[bob] ?: assertFail("No goodies for Bob")
        assertEquals(2, bobGoodies.size)

        val bankGoodies = bobGoodies.find { it.isIssuedBy(bankOfGoodies) } ?: assertFail("No goodies from Bank")
        assertEquals(bankCandy.quantity, bankGoodies.amount.quantity)

        val shopGoodies = bobGoodies.find { it.isIssuedBy(shopOfGoodies) } ?: assertFail("No goodies from Shop")
        assertEquals(shopCandy.quantity, shopGoodies.amount.quantity)
    }
}
