package com.example.goody.flows

import co.paralleluniverse.fibers.Suspendable
import com.example.goody.GoodyOps
import com.example.goody.contracts.Candy
import com.example.goody.flows.AbstractGoodyFlow.Companion.FINALISING
import com.example.goody.flows.AbstractGoodyFlow.Companion.STARTING
import com.example.goody.flows.AbstractGoodyFlow.Companion.SIGNING
import net.corda.core.contracts.Amount
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.ProgressTracker

@StartableByRPC
class GoodyExitFlow(
    private val candy: Amount<Candy>,
    private val issuerRef: OpaqueBytes
) : AbstractGoodyFlow(tracker()) {
    private companion object {
        private fun tracker() = ProgressTracker(STARTING, SIGNING, FINALISING)
    }

    @Throws(InsufficientGoodiesException::class)
    @Suspendable
    override fun call(): SignedTransaction {
        progressTracker.currentStep = STARTING
        val builder = TransactionBuilder(notary = null)
        val issuer = ourIdentity.ref(issuerRef)

        val (participants, keysForSigning) = GoodyOps.generateExit(
            serviceHub,
            builder,
            issuer,
            candy
        )

        // Sign transaction
        progressTracker.currentStep = SIGNING
        val tx = serviceHub.signInitialTransaction(builder, keysForSigning)

        // Commit the transaction
        progressTracker.currentStep = FINALISING
        return finaliseTx(tx, participants, "Unable to notarise exit")
    }
}