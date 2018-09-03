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
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.test.assertFailsWith

class GoodyTransferTest {
    private companion object {
        private val log = loggerFor<GoodyTransferTest>()
        private const val CANDY_TYPE = "Nougat"
        private val NOUGAT = Candy(CANDY_TYPE)
        private val initialCandy = Amount(7684, NOUGAT)
        private val GOODY_REFERENCE = OpaqueBytes(byteArrayOf(0x65, 0x33, 0x00, 0x24, 0x7C, 0x69))
    }

    private lateinit var mockNet: MockNetwork
    private lateinit var bankOfGoodiesNode: StartedMockNode
    private lateinit var bankOfGoodies: Party
    private lateinit var aliceNode: StartedMockNode
    private lateinit var alice: Party

    @Before
    fun start() {
        mockNet = MockNetwork(
            cordappPackages = listOf("com.example.goody.contracts", "com.example.goody.schemas")
        )
        bankOfGoodiesNode = mockNet.createPartyNode(BOG_NAME)
        bankOfGoodies = bankOfGoodiesNode.info.identityFromX500Name(BOG_NAME)
        aliceNode = mockNet.createPartyNode(ALICE_NAME)
        alice = aliceNode.info.singleIdentity()
        val future = bankOfGoodiesNode.startFlow(GoodyIssueFlow(initialCandy, GOODY_REFERENCE, mockNet.defaultNotaryIdentity))
        future.getOrThrow()
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun `transfer some goodies`() {
        val future = bankOfGoodiesNode.startFlow(GoodyTransferFlow(initialCandy, alice))
        mockNet.runNetwork()
        val transfer = future.getOrThrow().tx.outputsOfType<Goody.State>().groupBy(Goody.State::owner)
        val aliceGoodies = transfer[alice] ?: assertFail("Alice has received no goodies")
        log.info("Transfer TX: output={}", aliceGoodies)
    }

    @Test
    fun `transfer wrong kind of goodies`() {
        val niceStuff = Amount(1, Candy("Nice Stuff"))
        val future = bankOfGoodiesNode.startFlow(GoodyTransferFlow(niceStuff, alice))
        mockNet.runNetwork()
        assertFailsWith<InsufficientGoodiesException> {
            future.getOrThrow()
        }
    }

    @Test
    fun `transfer was complete`() {
        val future = bankOfGoodiesNode.startFlow(GoodyTransferFlow(initialCandy, alice))
        mockNet.runNetwork()
        val transfer = future.getOrThrow().tx.outputsOfType<Goody.State>().single()
        log.info("Transfer TX: output={}", transfer)

        val moreCandy = Amount(1, initialCandy.token)
        val failFuture = bankOfGoodiesNode.startFlow(GoodyTransferFlow(moreCandy, alice))
        mockNet.runNetwork()
        assertFailsWith<InsufficientGoodiesException> {
            failFuture.getOrThrow()
        }
    }

    @Test
    fun `transfer arrived correctly`() {
        val candy = initialCandy.splitEvenly(4).first()
        val toAliceFuture = bankOfGoodiesNode.startFlow(GoodyTransferFlow(candy, alice))
        mockNet.runNetwork()
        val toAliceGoodies = toAliceFuture.getOrThrow().tx.outputsOfType<Goody.State>().groupBy(Goody.State::owner)
        log.info("To Alice: output={}", toAliceGoodies)
        val aliceGoodies = toAliceGoodies[alice] ?: assertFail("Alice has received nothing")
        assertEquals(1, aliceGoodies.size)
        with(aliceGoodies[0].amount) {
            assertEquals(candy.quantity, quantity)
            assertEquals(candy.token, token.product)
        }

        val fromAliceFuture = aliceNode.startFlow(GoodyTransferFlow(candy, bankOfGoodies))
        mockNet.runNetwork()
        val fromAliceGoodies = fromAliceFuture.getOrThrow().tx.outputsOfType<Goody.State>().groupBy(Goody.State::owner)
        val bankGoodies = fromAliceGoodies[bankOfGoodies] ?: assertFail("Bank has received nothing")
        log.info("From Alice: output={}", fromAliceGoodies)
        assertEquals(1, bankGoodies.size)
        with(bankGoodies[0].amount) {
            assertEquals(candy.quantity, quantity)
            assertEquals(candy.token, token.product)
        }
    }

    @Test
    fun `transfer generates change`() {
        val candy = initialCandy.splitEvenly(4).first()
        val toAliceFuture = bankOfGoodiesNode.startFlow(GoodyTransferFlow(candy, alice))
        mockNet.runNetwork()
        val toAliceGoodies = toAliceFuture.getOrThrow().tx.outputsOfType<Goody.State>().groupBy(Goody.State::owner)
        log.info("To Alice: output={}", toAliceGoodies)
        val aliceGoodies = toAliceGoodies[alice] ?: assertFail("Alice has received nothing")
        assertEquals(1, aliceGoodies.size)
        with(aliceGoodies[0].amount) {
            assertEquals(candy.quantity, quantity)
            assertEquals(candy.token, token.product)
        }

        val bankChange = toAliceGoodies[bankOfGoodies] ?: assertFail("Bank has received no change")
        val expectedChange = initialCandy - candy
        assertEquals(1, bankChange.size)
        with(bankChange[0].amount) {
            assertEquals(expectedChange.quantity, quantity)
            assertEquals(expectedChange.token, token.product)
        }
    }

    @Test
    fun `total candy is transferred`() {
        val extraCandy = Amount(10000, initialCandy.token)
        val extraFuture = bankOfGoodiesNode.startFlow(GoodyIssueFlow(extraCandy, GOODY_REFERENCE, mockNet.defaultNotaryIdentity))
        extraFuture.getOrThrow()

        val allCandy = extraCandy + initialCandy
        val toAliceFuture = bankOfGoodiesNode.startFlow(GoodyTransferFlow(allCandy, alice))
        mockNet.runNetwork()
        val toAliceGoodies = toAliceFuture.getOrThrow().tx.outputsOfType<Goody.State>().groupBy(Goody.State::owner)
        log.info("To Alice: output={}", toAliceGoodies)
        val aliceGoodies = toAliceGoodies[alice] ?: assertFail("Alice has received no goodies")
        assertEquals(1, aliceGoodies.size)
        with(aliceGoodies[0].amount) {
            assertEquals(allCandy.quantity, quantity)
            assertEquals(initialCandy.token, token.product)
            assertEquals(bankOfGoodies, token.issuer.party)
            assertEquals(GOODY_REFERENCE, token.issuer.reference)
        }
    }

    @Test
    fun `candy can be transferred incrementally`() {
        initialCandy.splitEvenly(2).forEach { increment ->
            val toAliceFuture = bankOfGoodiesNode.startFlow(GoodyTransferFlow(increment, alice))
            mockNet.runNetwork()
            val toAliceGoodies = toAliceFuture.getOrThrow().tx.outputsOfType<Goody.State>().groupBy(Goody.State::owner)
            log.info("To Alice: output={}", toAliceGoodies)
            val aliceGoodies = toAliceGoodies[alice] ?: assertFail("Alice has received nothing")
            assertEquals(1, aliceGoodies.size)
            with(aliceGoodies[0].amount) {
                assertEquals(increment.quantity, quantity)
                assertEquals(increment.token, token.product)
            }
        }
    }
}
