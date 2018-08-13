package com.example.goody

import co.paralleluniverse.fibers.Suspendable
import com.example.goody.contracts.Candy
import com.example.goody.contracts.Goody
import com.example.goody.schemas.GoodySchemaV1
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.builder
import java.util.*
import java.util.concurrent.atomic.AtomicReference

class GoodySelection {
    companion object {
        private val instance = AtomicReference<GoodySelection>()

        fun getInstance(): GoodySelection {
            return instance.get() ?: { GoodySelection().apply(instance::set) }.invoke()
        }
    }

    @Suspendable
    fun unconsumedGoodyStates(services: ServiceHub,
                              amount: Amount<Candy>,
                              notary: Party? = null,
                              lockId: UUID): List<StateAndRef<Goody.State>> {
        val ourParties = services.keyManagementService.keys.map { key ->
            services.identityService.partyFromKey(key) ?: throw IllegalStateException("Unable to resolve party from key")
        }
        val fungibleCriteria = QueryCriteria.FungibleAssetQueryCriteria(owner = ourParties)

        val notaries = if (notary != null) listOf(notary) else services.networkMapCache.notaryIdentities
        val vaultCriteria = QueryCriteria.VaultQueryCriteria(notary = notaries)

        val logicalExpression = builder { GoodySchemaV1.PersistentGoodyState::type.equal(amount.token.type) }
        val goodyCriteria = QueryCriteria.VaultCustomQueryCriteria(logicalExpression)

        val fullCriteria = fungibleCriteria.and(vaultCriteria).and(goodyCriteria)

        return services.vaultService.tryLockFungibleStatesForSpending(lockId, fullCriteria, amount, Goody.State::class.java)
    }
}
