package com.example.goody.flows

import co.paralleluniverse.fibers.Suspendable
import com.example.goody.GoodyOps
import com.example.goody.contracts.Candy
import com.example.goody.flows.AbstractGoodyFlow.Companion.FINALISING
import com.example.goody.flows.AbstractGoodyFlow.Companion.SIGNING
import com.example.goody.flows.AbstractGoodyFlow.Companion.STARTING
import net.corda.core.contracts.Amount
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

@StartableByRPC
class GoodyTransferFlow(
    private val amount: Amount<Candy>,
    private val recipient: Party
) : AbstractGoodyFlow(tracker()) {
    private companion object {
        private fun tracker() = ProgressTracker(STARTING, SIGNING, FINALISING)
    }

    @Throws(InsufficientGoodiesException::class)
    @Suspendable
    override fun call(): SignedTransaction {
        progressTracker.currentStep = STARTING

        val builder = TransactionBuilder(notary = null)
        val (transferTX, keysForSigning) = GoodyOps.generateTransfer(
            serviceHub,
            builder,
            recipient,
            amount
        )

        progressTracker.currentStep = SIGNING
        val tx = serviceHub.signInitialTransaction(transferTX, keysForSigning)

        progressTracker.currentStep = FINALISING
        return finaliseTx(tx, setOf(recipient), "Unable to notarise transfer")
    }
}
