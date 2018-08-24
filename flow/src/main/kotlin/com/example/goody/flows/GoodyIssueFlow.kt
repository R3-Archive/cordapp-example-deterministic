package com.example.goody.flows

import com.example.goody.GoodyOps
import com.example.goody.contracts.Candy
import com.example.goody.contracts.issuedBy
import com.example.goody.flows.AbstractGoodyFlow.Companion.FINALISING
import com.example.goody.flows.AbstractGoodyFlow.Companion.SIGNING
import com.example.goody.flows.AbstractGoodyFlow.Companion.STARTING
import net.corda.core.contracts.Amount
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.ProgressTracker

@StartableByRPC
class GoodyIssueFlow(
    private val candy: Amount<Candy>,
    private val issuerRef: OpaqueBytes,
    private val notary: Party
) : AbstractGoodyFlow(tracker()) {
    private companion object {
        private fun tracker() = ProgressTracker(STARTING, SIGNING, FINALISING)
    }

    override fun call(): SignedTransaction {
        progressTracker.currentStep = STARTING
        val builder = TransactionBuilder(notary)
        val issuer = ourIdentity.ref(issuerRef)
        val signers = GoodyOps.generateIssue(builder, candy.issuedBy(issuer), ourIdentity, notary)
        progressTracker.currentStep = SIGNING
        val tx = serviceHub.signInitialTransaction(builder, signers)
        progressTracker.currentStep = FINALISING
        return finaliseTx(tx, emptySet(), "Unable to notarise issue")
    }
}
