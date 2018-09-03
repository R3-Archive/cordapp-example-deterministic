package com.example.goody

import co.paralleluniverse.fibers.Suspendable
import com.example.goody.contracts.Candy
import com.example.goody.contracts.Goody
import com.example.goody.contracts.issuedBy
import com.example.goody.flows.InsufficientGoodiesException
import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.DEFAULT_PAGE_NUM
import net.corda.core.node.services.vault.PageSpecification
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.TransactionBuilder
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
        val availableGoodies = goodySelection.unconsumedGoodyStates(services, targetAmount, tx.lockId, tx.notary)
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

    @Throws(InsufficientGoodiesException::class)
    @Suspendable
    fun generateExit(services: ServiceHub,
                     tx: TransactionBuilder,
                     issuer: PartyAndReference,
                     targetAmount: Amount<Candy>): Pair<Set<Party>, Set<PublicKey>> {
        val goodySelection = GoodySelection.getInstance()
        val availableGoodies = goodySelection.unconsumedGoodyStates(
            services,
            targetAmount,
            tx.lockId,
            tx.notary,
            setOf(issuer.party),
            setOf(issuer.reference)
        )
        if (availableGoodies.isEmpty()) {
            throw InsufficientGoodiesException("Not enough ${targetAmount.token.type} available")
        }

        val signers = generateExit(tx, targetAmount.issuedBy(issuer), availableGoodies)

        // Work out who the owners of the burnt states were (specify page size so we don't silently drop any if > DEFAULT_PAGE_SIZE)
        val inputStates = services.vaultService.queryBy<Goody.State>(QueryCriteria.VaultQueryCriteria(stateRefs = tx.inputStates()),
            PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = tx.inputStates().size)).states

        val participants: Set<Party> = inputStates
            .mapNotNull { services.identityService.wellKnownPartyFromAnonymous(it.state.data.owner) }
            .toSet()

        return Pair(participants, signers)
    }

    private fun generateExit(tx: TransactionBuilder,
                             targetAmount: Amount<Issued<Candy>>,
                             available: List<StateAndRef<Goody.State>>): Set<PublicKey> {
        require(available.isNotEmpty()) { "List of states to exit cannot be empty." }
        var exitable = available.filter { (state) -> state.data.amount.token == targetAmount.token }
        tx.notary = exitable.firstOrNull()?.state?.notary
        exitable = exitable.filter { it.state.notary == tx.notary }

        val moveKeys = mutableSetOf<PublicKey>()
        val exitKeys = mutableSetOf<PublicKey>()
        var totalExited = 0L

        for (input in exitable) {
            if (totalExited >= targetAmount.quantity) {
                break
            }

            val exitableData = input.state.data
            totalExited += exitableData.amount.quantity
            exitKeys += exitableData.owner.owningKey
            moveKeys += exitableData.exitKeys
            tx.addInputState(input)

            val change = if (totalExited > targetAmount.quantity) totalExited - targetAmount.quantity else 0L
            if (change > 0) {
                val changeAmount = Amount(change, exitableData.amount.token)
                tx.addOutputState(deriveState(input.state, changeAmount, input.state.data.owner))
                break
            }
        }

        tx.addCommand(generateMoveCommand(), moveKeys.toList())
        tx.addCommand(generateExitCommand(targetAmount), exitKeys.toList())
        return moveKeys + exitKeys
    }

    fun generateMoveCommand() = Goody.Commands.Move()
    fun generateExitCommand(amount: Amount<Issued<Candy>>) = Goody.Commands.Exit(amount)
}
