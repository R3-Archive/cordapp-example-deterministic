package org.testing.goody.contracts

import net.corda.core.contracts.*
import net.corda.core.crypto.toStringShort
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.transactions.LedgerTransaction
import org.testing.goody.schemas.GoodySchemaV1

class Goody : Contract {
    companion object {
        const val PROGRAM_ID = "org.testing.goody.contracts.Goody"
    }

    interface Commands : CommandData {
        class Issue : TypeOnlyCommandData()
        data class Move(override val contract: Class<out Contract>? = null) : MoveCommand
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
        // Verify just this contract.
    }
}
