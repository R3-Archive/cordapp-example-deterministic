package com.example.goody.contracts

import com.example.goody.schemas.GoodySchemaV1
import net.corda.core.contracts.*
import net.corda.core.contracts.Amount.Companion.sumOrNull
import net.corda.core.contracts.Amount.Companion.sumOrThrow
import net.corda.core.contracts.Amount.Companion.sumOrZero
import net.corda.core.crypto.toStringShort
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey

class Goody : Contract {
    companion object {
        const val PROGRAM_ID = "com.example.goody.contracts.Goody"
    }

    interface Commands : CommandData {
        class Issue : TypeOnlyCommandData()
        data class Move(override val contract: Class<out Contract>? = null) : MoveCommand
        data class Exit(val amount: Amount<Issued<Candy>>) : CommandData
    }

    data class State(override val owner: AbstractParty, override val amount: Amount<Issued<Candy>>) : FungibleAsset<Candy>, QueryableState {
        override val participants = listOf(owner)
        override val exitKeys = setOf(owner.owningKey, amount.token.issuer.party.owningKey)

        override fun withNewOwner(newOwner: AbstractParty) = CommandAndState(Commands.Move(), copy(owner = newOwner))
        override fun withNewOwnerAndAmount(newAmount: Amount<Issued<Candy>>, newOwner: AbstractParty): State
                     = copy(amount = amount.copy(newAmount.quantity), owner = newOwner)

        fun isIssuedBy(issuer: Party): Boolean = amount.token.issuer.party == issuer

        override fun generateMappedObject(schema: MappedSchema): PersistentState {
            return when (schema) {
                is GoodySchemaV1 -> GoodySchemaV1.PersistentGoodyState(
                    owner = this.owner,
                    count = this.amount.quantity,
                    type = this.amount.token.product.type,
                    issuerPartyHash = this.amount.token.issuer.party.owningKey.toStringShort(),
                    issuerRef = this.amount.token.issuer.reference.bytes
                )
                else -> throw IllegalArgumentException("Unrecognised schema $schema")
            }
        }

        override fun supportedSchemas(): List<MappedSchema> = listOf(GoodySchemaV1)
    }

    override fun verify(tx: LedgerTransaction) {
        val groups = tx.groupStates { it: Goody.State -> it.amount.token }

        for ((inputs, outputs, key) in groups) {
            // Either inputs or outputs could be empty.
            val issuer = key.issuer
            val candy = key.product

            requireThat {
                "there are no zero sized outputs" using outputs.none { it.amount.quantity == 0L }
            }

            val issueCommand = tx.commands.select<Commands.Issue>().firstOrNull()
            if (issueCommand != null) {
                verifyIssueCommand(inputs, outputs, tx, issueCommand, candy, issuer)
            } else {
                val inputAmount = inputs.sumCandyOrNull() ?: throw IllegalArgumentException("There is at least one candy input for this group")
                val outputAmount = outputs.sumCandyOrZero(candy.issuedBy(issuer))

                // If we want to remove candy from the ledger, that must be signed for by the issuer.
                // A mis-signed or duplicated exit command will just be ignored here and result in the exit amount being zero.
                val exitKeys: Set<PublicKey> = inputs.flatMap { it.exitKeys }.toSet()
                val exitCommand = tx.commands.select<Commands.Exit>(parties = null, signers = exitKeys).singleOrNull { it.value.amount.token == key }
                val amountExitingLedger = exitCommand?.value?.amount ?: Amount.zero(candy.issuedBy(issuer))

                requireThat {
                    "there are no zero sized inputs" using inputs.none { it.amount.quantity == 0L }
                    "for reference ${issuer.reference} at issuer ${issuer.party} the amounts balance: ${inputAmount.quantity} - ${amountExitingLedger.quantity} != ${outputAmount.quantity}" using
                            (inputAmount == outputAmount + amountExitingLedger)
                }

                verifyMoveCommand<Commands.Move>(inputs, tx.commands)
            }
        }
    }

    private fun verifyIssueCommand(inputs: List<State>,
                                   outputs: List<State>,
                                   tx: LedgerTransaction,
                                   issueCommand: CommandWithParties<Commands.Issue>,
                                   candy: Candy,
                                   issuer: PartyAndReference) {
        // If we have an issue command, perform special processing: the group is allowed to have no inputs,
        // and the output states must have a deposit reference owned by the signer.
        //
        // Whilst the transaction *may* have no inputs, it can have them, and in this case the outputs must
        // sum to more than the inputs. An issuance of zero size is not allowed.
        //
        // Note that this means literally anyone with access to the network can issue candy claims of arbitrary
        // amounts! It is up to the recipient to decide if the backing party is trustworthy or not, via some
        // as-yet-unwritten identity service. See ADP-22 for discussion.

        // The grouping ensures that all outputs have the same deposit reference and candy type.
        val inputAmount = inputs.sumCandyOrZero(candy.issuedBy(issuer))
        val outputAmount = outputs.sumCandy()
        val candyCommands = tx.commands.select<Commands.Issue>()
        requireThat {
            "output states are issued by a command signer" using (issuer.party.owningKey in issueCommand.signers)
            "output values sum to more than the inputs" using (outputAmount > inputAmount)
            "there is only a single issue command" using (candyCommands.count() == 1)
        }
    }
}

/** Sums the candy states in the list, returning zero of the given candy+issuer if there is none. */
fun Iterable<Goody.State>.sumCandyOrZero(candy: Issued<Candy>): Amount<Issued<Candy>> {
    return filterIsInstance<Goody.State>().map(Goody.State::amount).sumOrZero(candy)
}

/** Sums the candy states in the list, returning null if there is none. */
fun Iterable<Goody.State>.sumCandyOrNull(): Amount<Issued<Candy>>? = filterIsInstance<Goody.State>().map(Goody.State::amount).sumOrNull()

fun Iterable<Goody.State>.sumCandy(): Amount<Issued<Candy>> = filterIsInstance<Goody.State>().map(Goody.State::amount).sumOrThrow()
