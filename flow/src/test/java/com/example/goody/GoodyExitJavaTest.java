package com.example.goody;

import com.example.goody.contracts.Candy;
import com.example.goody.contracts.Goody;
import com.example.goody.flows.GoodyExitFlow;
import com.example.goody.flows.GoodyIssueFlow;
import com.example.goody.flows.InsufficientGoodiesException;
import net.corda.core.concurrent.CordaFuture;
import net.corda.core.contracts.Amount;
import net.corda.core.contracts.Issued;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.utilities.OpaqueBytes;
import net.corda.testing.node.MockNetwork;
import net.corda.testing.node.StartedMockNode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static com.example.goody.Constants.BOG_NAME;
import static com.example.goody.Utils.byOwner;
import static com.example.goody.contracts.CandyKt.*;
import static java.util.Arrays.asList;
import static net.corda.core.utilities.KotlinUtilsKt.getOrThrow;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class GoodyExitJavaTest {
    private static final Logger LOG = LoggerFactory.getLogger(GoodyExitJavaTest.class);
    private static final String CANDY_TYPE = "Nougat";
    private static final Candy NOUGAT = new Candy(CANDY_TYPE);
    private static final Amount<Candy> INITIAL_CANDY = new Amount<>(7684, NOUGAT);
    private static final OpaqueBytes GOODY_REFERENCE = new OpaqueBytes(new byte[] { 0x65, 0x33, 0x00, 0x24, 0x7C, 0x69 });

    private MockNetwork mockNet;
    private StartedMockNode bankOfGoodiesNode;
    private Party bankOfGoodies;

    @Before
    public void start() {
        mockNet = new MockNetwork(
            asList("com.example.goody.contracts", "com.example.goody.schemas")
        );
        bankOfGoodiesNode = mockNet.createPartyNode(BOG_NAME);
        bankOfGoodies = bankOfGoodiesNode.getInfo().identityFromX500Name(BOG_NAME);
        CordaFuture<SignedTransaction> future = bankOfGoodiesNode.startFlow(new GoodyIssueFlow(INITIAL_CANDY, GOODY_REFERENCE, mockNet.getDefaultNotaryIdentity()));
        getOrThrow(future, null);
    }

    @After
    public void cleanUp() {
        mockNet.stopNodes();
    }

    @Test
    public void testIssuedGoodiesCanBeExited() {
        Amount<Candy> exitCandy = div(INITIAL_CANDY, 3);
        CordaFuture<SignedTransaction> future = bankOfGoodiesNode.startFlow(new GoodyExitFlow(exitCandy, GOODY_REFERENCE));
        mockNet.runNetwork();
        Map<AbstractParty, List<Goody.State>> exited = byOwner(getOrThrow(future, null).getTx().outputsOfType(Goody.State.class));
        List<Goody.State> burntGoodies = exited.get(bankOfGoodies);
        assertNotNull("No goodies have exited the ledger", burntGoodies);
        LOG.info("Exit TX: output={}", burntGoodies);

        Amount<Candy> remainingCandy = INITIAL_CANDY.minus(exitCandy);
        assertEquals(1, burntGoodies.size());
        Amount<Issued<Candy>> burntAmount = burntGoodies.get(0).getAmount();
        assertEquals(remainingCandy.getQuantity(), burntAmount.getQuantity());
        assertEquals(remainingCandy.getToken(), burntAmount.getToken().getProduct());
    }

    @Test(expected = InsufficientGoodiesException.class)
    public void testCannotExitMoreGoodiesThanExist() {
        Amount<Candy> exitCandy = plus(INITIAL_CANDY, 1);
        CordaFuture<SignedTransaction> future = bankOfGoodiesNode.startFlow(new GoodyExitFlow(exitCandy, GOODY_REFERENCE));
        mockNet.runNetwork();
        byOwner(getOrThrow(future, null).getTx().outputsOfType(Goody.State.class));
    }
}
