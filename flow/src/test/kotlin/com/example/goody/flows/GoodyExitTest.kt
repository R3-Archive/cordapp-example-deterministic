package com.example.goody.flows

import com.example.goody.BOG_NAME
import com.example.goody.assertFail
import com.example.goody.contracts.*
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
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class GoodyExitTest {
    private companion object {
        private val log = loggerFor<GoodyExitTest>()
        private const val CANDY_TYPE = "Nougat"
        private val NOUGAT = Candy(CANDY_TYPE)
        private val initialCandy = Amount(7684, NOUGAT)
        private val GOODY_REFERENCE = OpaqueBytes(byteArrayOf(0x65, 0x33, 0x00, 0x24, 0x7C, 0x69))
    }

    private lateinit var mockNet: MockNetwork
    private lateinit var bankOfGoodiesNode: StartedMockNode
    private lateinit var bankOfGoodies: Party

    @Before
    fun start() {
        mockNet = MockNetwork(
            cordappPackages = listOf("com.example.goody.contracts", "com.example.goody.schemas")
        )
        bankOfGoodiesNode = mockNet.createPartyNode(BOG_NAME)
        bankOfGoodies = bankOfGoodiesNode.info.identityFromX500Name(BOG_NAME)
        val future = bankOfGoodiesNode.startFlow(GoodyIssueFlow(initialCandy, GOODY_REFERENCE, mockNet.defaultNotaryIdentity))
        future.getOrThrow()
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun `test issued goodies can be exited`() {
        val exitCandy = initialCandy / 3
        val future = bankOfGoodiesNode.startFlow(GoodyExitFlow(exitCandy, GOODY_REFERENCE))
        mockNet.runNetwork()
        val exited = future.getOrThrow().tx.outputsOfType<Goody.State>().groupBy(Goody.State::owner)
        val burntGoodies = exited[bankOfGoodies] ?: assertFail("No goodies have exited the ledger")
        log.info("Exit TX: output={}", burntGoodies)

        val remainingCandy = initialCandy - exitCandy
        assertEquals(1, burntGoodies.size)
        with(burntGoodies[0].amount) {
            assertEquals(remainingCandy.quantity, quantity)
            assertEquals(remainingCandy.token, token.product)
        }
    }

    @Test(expected = InsufficientGoodiesException::class)
    fun `test we cannot exit more goodies than exist`() {
        val exitCandy = initialCandy + 1
        val future = bankOfGoodiesNode.startFlow(GoodyExitFlow(exitCandy, GOODY_REFERENCE))
        mockNet.runNetwork()
        future.getOrThrow().tx.outputsOfType<Goody.State>().groupBy(Goody.State::owner)
    }
}