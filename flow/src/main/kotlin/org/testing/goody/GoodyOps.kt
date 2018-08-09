package org.testing.goody

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.transactions.TransactionBuilder
import org.testing.goody.contracts.Candy
import org.testing.goody.contracts.Goody
import org.testing.goody.flows.InsufficientGoodiesException
import java.security.PublicKey

object GoodyOps {
    private fun deriveState(txState: TransactionState<Goody.State>, amount: Amount<Issued<Candy>>, owner: AbstractParty)
        = txState.copy(data = txState.data.copy(amount = amount, owner = owner))

    fun generateIssue(tx: TransactionBuilder, amount: Amount<Issued<Candy>>, owner: AbstractParty, notary: Party)
        = generateIssue(tx, TransactionState(Goody.State(owner, amount), Goody.PROGRAM_ID, notary), Goody.Commands.Issue())

    fun generateIssue(tx: TransactionBuilder,
                      transactionState: TransactionState<Goody.State>,
                      issueCommand: CommandData): Set<PublicKey> {
        check(tx.inputStates().isEmpty())
        check(tx.outputStates().map(TransactionState<*>::data).filterIsInstance(transactionState.javaClass).isEmpty())
        require(transactionState.data.amount.quantity > 0)
        val at = transactionState.data.amount.token.issuer
        val commandSigner = at.party.owningKey
        tx.addOutputState(transactionState)
        tx.addCommand(issueCommand, commandSigner)
        return setOf(commandSigner)
    }

    @Throws(InsufficientGoodiesException::class)
    @Suspendable
    fun generateTransfer(services: ServiceHub,
                         tx: TransactionBuilder,
                         recipient: Party,
                         targetAmount: Amount<Candy>): Pair<TransactionBuilder, List<PublicKey>> {
        val goodySelection = GoodySelection.getInstance()
        val availableGoodies = goodySelection.unconsumedGoodyStates(services, targetAmount, tx.notary, tx.lockId)
        if (availableGoodies.isEmpty()) {
            throw InsufficientGoodiesException("No ${targetAmount.token.type} available")
        }

        tx.notary = availableGoodies.first().state.notary

        val keysUsed = mutableSetOf<PublicKey>()
        val templateStates = mutableMapOf<PartyAndReference, TransactionState<Goody.State>>()
        val totalCandies = mutableMapOf<PartyAndReference, Long>()
        var totalSpent = 0L

        for (input in availableGoodies) {
            if (totalSpent >= targetAmount.quantity) {
                break
            }

            val availableAmount = input.state.data.amount
            val issuer = availableAmount.token.issuer
            templateStates.putIfAbsent(issuer, input.state)

            totalSpent += availableAmount.quantity
            val change = if (totalSpent > targetAmount.quantity) totalSpent - targetAmount.quantity else 0L
            val nextSpend = availableAmount.quantity - change
            totalCandies.compute(issuer) { _, candies -> (candies ?: 0L) + nextSpend }

            keysUsed.add(input.state.data.owner.owningKey)
            tx.addInputState(input)

            // Any unspent change from this state is assigned back to its original owner
            // as another output state.
            if (change > 0) {
                val changeAmount = Amount(change, availableAmount.token)
                tx.addOutputState(deriveState(input.state, changeAmount, input.state.data.owner))
                break
            }
        }

        templateStates.map { entry ->
            val outputState = deriveState(
                entry.value,
                Amount(totalCandies[entry.key] ?: 0, Issued(entry.key, targetAmount.token)), recipient
            )
            tx.addOutputState(outputState)
        }

        return keysUsed.toList().let { keys ->
            tx.addCommand(generateMoveCommand(), keys)
            Pair(tx, keys)
        }
    }

    fun generateMoveCommand() = Goody.Commands.Move()
}
