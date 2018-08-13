package com.example.goody.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.NotaryException
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker

abstract class AbstractGoodyFlow(override val progressTracker: ProgressTracker) : FlowLogic<SignedTransaction>() {
    @Suspendable
    protected fun finaliseTx(tx: SignedTransaction, extraParticipants: Set<Party>, message: String): SignedTransaction {
        try {
            return subFlow(FinalityFlow(tx, extraParticipants))
        } catch (e: NotaryException) {
            throw GoodyException(message, e)
        }
    }
}
