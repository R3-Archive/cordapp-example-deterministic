package com.example.goody.flows

import com.example.goody.GoodyOps
import com.example.goody.contracts.Candy
import net.corda.core.contracts.Amount
import net.corda.core.contracts.Issued
import net.corda.core.contracts.PartyAndReference
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.ProgressTracker

@StartableByRPC
class GoodyIssueFlow(
    private val candy: Amount<Candy>,
    private val issuerPartyRef: OpaqueBytes,
    private val notary: Party
) : AbstractGoodyFlow(tracker()) {
    companion object {
        object STARTING : ProgressTracker.Step("Starting")
        object SIGNING : ProgressTracker.Step("Signing")
        object FINALISING : ProgressTracker.Step("Finalising")

        private fun tracker() = ProgressTracker(STARTING, SIGNING, FINALISING)
    }

    override fun call(): SignedTransaction {
        progressTracker.currentStep = STARTING
        val builder = TransactionBuilder(notary)
        val issuer = ourIdentity.ref(issuerPartyRef)
        val signers = GoodyOps.generateIssue(builder, candy.issuedBy(issuer), ourIdentity, notary)
        progressTracker.currentStep = SIGNING
        val tx = serviceHub.signInitialTransaction(builder, signers)
        progressTracker.currentStep = FINALISING
        return finaliseTx(tx, emptySet(), "Unable to notarise issue")
    }
}

infix fun Amount<Candy>.issuedBy(deposit: PartyAndReference) = Amount(quantity, displayTokenSize, token.issuedBy(deposit))
infix fun Candy.issuedBy(deposit: PartyAndReference) = Issued(deposit, this)
