package com.example.goody.api

import com.example.goody.flows.GoodyIssueFlow
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import javax.ws.rs.*
import javax.ws.rs.client.InvocationCallback
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

    @POST
    @Path("issue")
    @Consumes(APPLICATION_JSON)
    fun issue(request: IssueRequest?): Response {
        val issueRequest = request ?: throw BadRequestException("Request data missing")
        log.info("Received Issue request: candy='{}', reference={}", issueRequest.candy, issueRequest.issuerReference)
        return try {
            val signedTx = rpcOps.startFlow(::GoodyIssueFlow, issueRequest.candy, issueRequest.issuerReference, rpcOps.notaryIdentities().first())
                    .returnValue.getOrThrow()
            Response.ok("Transaction ID: ${signedTx.id}").build()
        } catch (ex: Exception) {
            log.error("Issue failed: ${ex.message}", ex)
            throw BadRequestException(ex.message)
        }
    }

    @POST
    @Path("issueAsync")
    @Consumes(APPLICATION_JSON)
    fun asyncIssue(request: IssueRequest?, @Suspended async: AsyncResponse) {
        val issueRequest = request ?: throw BadRequestException("Request data missing")
        log.info("Received Issue request: candy='{}', reference={}", issueRequest.candy, issueRequest.issuerReference)
        async.register(CompletionCallback { ex ->
            if (ex == null) log.info("Issue complete") else log.error("Issue failed", ex)
        })

        thread(isDaemon = true) {
            try {
                val signedTx = rpcOps.startFlow(::GoodyIssueFlow, issueRequest.candy, issueRequest.issuerReference, rpcOps.notaryIdentities().first())
                        .returnValue.getOrThrow()
                async.resume(Response.ok("Transaction ID: ${signedTx.id}").build())
            } catch (ex: Exception) {
                async.resume(BadRequestException(ex.message))
            }
        }
    }
}
