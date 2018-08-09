package org.testing.goody.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Amount
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import org.testing.goody.GoodyOps
import org.testing.goody.contracts.Candy

@StartableByRPC
class GoodyTransferFlow(
    private val amount: Amount<Candy>,
    private val recipient: Party
) : AbstractGoodyFlow(tracker()) {
    companion object {
        object STARTING : ProgressTracker.Step("Starting")
        object SIGNING : ProgressTracker.Step("Signing")
        object FINALISING : ProgressTracker.Step("Finalising")

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
