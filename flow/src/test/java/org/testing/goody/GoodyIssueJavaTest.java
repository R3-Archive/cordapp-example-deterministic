package org.testing.goody;

import net.corda.core.concurrent.CordaFuture;
import net.corda.core.contracts.Amount;
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
import org.testing.goody.contracts.Candy;
import org.testing.goody.contracts.Goody;
import org.testing.goody.flows.GoodyIssueFlow;

import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;
import static net.corda.core.utilities.KotlinUtilsKt.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.testing.goody.Constants.BOG_NAME;
import static org.testing.goody.Utils.byOwner;

public class GoodyIssueJavaTest {
    private static final Logger LOG = LoggerFactory.getLogger(GoodyIssueJavaTest.class);
    private static final OpaqueBytes GOODY_REFERENCE = new OpaqueBytes(new byte[] { 0x65, 0x33, 0x00, 0x24, 0x7C, 0x69 });

    private MockNetwork mockNet;
    private StartedMockNode bankOfGoodiesNode;
    private Party bankOfGoodies;
    private Party notary;

    @Before
    public void start() {
        mockNet = new MockNetwork(
            asList("org.testing.goody.contracts", "org.testing.goody.schemas")
        );
        bankOfGoodiesNode = mockNet.createPartyNode(BOG_NAME);
        bankOfGoodies = bankOfGoodiesNode.getInfo().identityFromX500Name(BOG_NAME);
        notary = mockNet.getDefaultNotaryIdentity();
    }

    @After
    public void cleanUp() {
        mockNet.stopNodes();
    }

    @Test
    public void issueSomeGoodies() {
        Amount<Candy> expected = new Amount<>(1002, new Candy("Toffee"));
        CordaFuture<SignedTransaction> future = bankOfGoodiesNode.startFlow(new GoodyIssueFlow(expected, GOODY_REFERENCE, notary));
        mockNet.runNetwork();
        SignedTransaction issueTx = getOrThrow(future,null);
        LOG.info("Issue TX: {}", issueTx);

        Map<AbstractParty, List<Goody.State>> output = byOwner(issueTx.getTx().outputsOfType(Goody.State.class));
        List<Goody.State> bankGoodies = output.get(bankOfGoodies);
        assertNotNull("Bank has issued no goodies", bankGoodies);
        assertEquals(1, bankGoodies.size());
        assertEquals(expected.getQuantity(), bankGoodies.get(0).getAmount().getQuantity());
    }
}
