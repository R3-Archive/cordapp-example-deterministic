package com.example.goody.api

import com.example.goody.contracts.Candy
import com.example.goody.contracts.Goody
import com.example.goody.flows.GoodyIssueFlow
import com.example.goody.flows.GoodyTransferFlow
import com.example.goody.schemas.GoodySchemaV1
import net.corda.core.contracts.Amount
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.Vault.StateModificationStatus.MODIFIABLE
import net.corda.core.node.services.vault.builder
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import javax.ws.rs.*
import javax.ws.rs.container.AsyncResponse
import javax.ws.rs.container.CompletionCallback
import javax.ws.rs.container.Suspended
import javax.ws.rs.core.MediaType.*
import javax.ws.rs.core.Response
import kotlin.concurrent.thread

@Path("goody")
class GoodyApi(private val rpcOps: CordaRPCOps) {
    private companion object {
        private val log = loggerFor<GoodyApi>()
    }

    private val myLegalName: List<CordaX500Name> get() = rpcOps.nodeInfo().legalIdentities.map(Party::name)

    /**
     * Returns the node's name(s).
     */
    @GET
    @Path("me")
    @Produces(APPLICATION_JSON)
    fun whoami() = mapOf("me" to myLegalName)

    /**
     * Submits an "Issue" request for more Candy.
     */
    @POST
    @Path("issue")
    @Consumes(APPLICATION_JSON)
    fun issue(request: IssueRequest?, @Suspended async: AsyncResponse) {
        async.register(CompletionCallback { ex ->
            if (ex == null) log.info("Issue completed") else log.error("Issue failed", ex)
        })
        val issueRequest = request ?: throw BadRequestException("Request data missing")
        log.info("Received Issue request: candy='{}', reference={}", issueRequest.candy, issueRequest.issuerReference)

        thread(isDaemon = true) {
            try {
                val notary = issueRequest.notary ?: rpcOps.notaryIdentities().first()
                val signedTx = rpcOps.startFlow(::GoodyIssueFlow, issueRequest.candy, issueRequest.issuerReference, notary)
                        .returnValue.getOrThrow()
                async.resume(Response.ok("Transaction ID: ${signedTx.id}").build())
            } catch (ex: Exception) {
                async.resume(BadRequestException(ex.message))
            }
        }
    }

    /**
     * Submits a "Transfer" request for some Candy to another party.
     */
    @POST
    @Path("transfer")
    @Consumes(APPLICATION_JSON)
    fun transfer(request: TransferRequest?, @Suspended async: AsyncResponse) {
        async.register(CompletionCallback { ex ->
            if (ex == null) log.info("Transfer completed") else log.error("Transfer failed", ex)
        })

        val transferRequest = request ?: throw BadRequestException("Request data missing")
        log.info("Received Transfer request: candy='{}', recipient='{}'", transferRequest.candy, transferRequest.recipient)

        thread(isDaemon = true) {
            try {
                val signedTx = rpcOps.startFlow(::GoodyTransferFlow, transferRequest.candy, transferRequest.recipient)
                        .returnValue.getOrThrow()
                async.resume(Response.ok("Transaction ID: ${signedTx.id}").build())
            } catch (ex: Exception) {
                async.resume(BadRequestException(ex.message))
            }
        }
    }

    /**
     * Requests how many of each type or Candy we currently own.
     */
    @GET
    @Path("balances")
    @Produces(APPLICATION_JSON)
    @Consumes(APPLICATION_JSON, APPLICATION_FORM_URLENCODED)
    fun balances(@QueryParam("candy") candyType: String?, @Suspended async: AsyncResponse) {
        async.register(CompletionCallback { ex ->
            if (ex == null) log.info("Balance request completed") else log.error("Balance request failed", ex)
        })

        log.info("Received Balance request: candy='{}'", candyType ?: "ALL")

        thread(isDaemon = true) {
            val balances = try {
                rowsToBalances(rpcOps.vaultQueryBy<Goody.State>(generateCandySumCriteria(candyType)).otherResults)
            } catch (ex: Exception) {
                async.resume(BadRequestException(ex.message))
                return@thread
            }
            async.resume(Response.ok(mapOf("balances" to balances)).build())
        }
    }

    private fun generateCandySumCriteria(candyType: String?): QueryCriteria {
        val sum = builder { GoodySchemaV1.PersistentGoodyState::count.sum(groupByColumns = listOf(GoodySchemaV1.PersistentGoodyState::type)) }
        return QueryCriteria.VaultCustomQueryCriteria(sum).let { sumCriteria ->
            if (candyType != null) {
                val candyIndex = builder { GoodySchemaV1.PersistentGoodyState::type.equal(candyType.toUpperCase()) }
                // This query should only return states the calling node is a participant of (meaning they can be modified/spent).
                sumCriteria.and(QueryCriteria.VaultCustomQueryCriteria(candyIndex, isModifiable = MODIFIABLE))
            } else {
                sumCriteria
            }
        }
    }

    private fun rowsToBalances(rows: List<Any>): Map<Candy, Amount<Candy>> {
        val balances = mutableMapOf<Candy, Amount<Candy>>()
        for (index in 0 until rows.size step 2) {
            val candy = Candy(rows[index + 1] as String)
            balances[candy] = Amount(rows[index] as Long, candy)
        }
        return balances
    }
}
