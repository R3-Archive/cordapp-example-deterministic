package com.example.goody.api

import com.example.goody.flows.GoodyIssueFlow
import com.example.goody.flows.GoodyTransferFlow
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
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
}
