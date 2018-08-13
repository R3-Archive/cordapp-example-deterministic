package com.example.goody;

import com.example.goody.contracts.Candy;
import com.example.goody.contracts.Goody;
import com.example.goody.flows.GoodyIssueFlow;
import com.example.goody.flows.GoodyTransferFlow;
import com.example.goody.flows.InsufficientGoodiesException;
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

import java.util.List;
import java.util.Map;

import static com.example.goody.Constants.BOG_NAME;
import static com.example.goody.Utils.byOwner;
import static java.util.Arrays.asList;
import static net.corda.core.utilities.KotlinUtilsKt.*;
import static net.corda.testing.core.TestConstants.ALICE_NAME;
import static net.corda.testing.core.TestUtils.singleIdentity;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class GoodyTransferJavaTest {
    private static final Logger LOG = LoggerFactory.getLogger(GoodyTransferJavaTest.class);
    private static final String CANDY_TYPE = "Nougat";
    private static final Candy NOUGAT = new Candy(CANDY_TYPE);
    private static final Amount<Candy> INITIAL_CANDY = new Amount<>(7684, NOUGAT);
    private static final OpaqueBytes GOODY_REFERENCE = new OpaqueBytes(new byte[] { 0x65, 0x33, 0x00, 0x24, 0x7C, 0x69 });

    private MockNetwork mockNet;
    private StartedMockNode bankOfGoodiesNode;
    private StartedMockNode aliceNode;
    private Party bankOfGoodies;
    private Party alice;

    @Before
    public void start() {
        mockNet = new MockNetwork(
            asList("com.example.goody.contracts", "com.example.goody.schemas")
        );
        bankOfGoodiesNode = mockNet.createPartyNode(BOG_NAME);
        bankOfGoodies = bankOfGoodiesNode.getInfo().identityFromX500Name(BOG_NAME);
        aliceNode = mockNet.createPartyNode(ALICE_NAME);
        alice = singleIdentity(aliceNode.getInfo());
        CordaFuture<SignedTransaction> future = bankOfGoodiesNode.startFlow(new GoodyIssueFlow(INITIAL_CANDY, GOODY_REFERENCE, mockNet.getDefaultNotaryIdentity()));
        getOrThrow(future, null);
    }

    @After
    public void cleanUp() {
        mockNet.stopNodes();
    }

    @Test
    public void transferSomeGoodies() {
        CordaFuture<SignedTransaction> future = bankOfGoodiesNode.startFlow(new GoodyTransferFlow(INITIAL_CANDY, alice));
        mockNet.runNetwork();
        Map<AbstractParty, List<Goody.State>> transfer = byOwner(getOrThrow(future, null).getTx().outputsOfType(Goody.State.class));
        List<Goody.State> aliceGoodies = transfer.get(alice);
        assertEquals(1, aliceGoodies.size());
        assertNotNull("Alice has received no goodies", aliceGoodies);
        LOG.info("Transfer TX: output={}", aliceGoodies);
    }

    @Test(expected = InsufficientGoodiesException.class)
    public void transferWrongKindOfGoodies() {
        Amount<Candy> niceStuff = new Amount<>(1, new Candy("Nice Stuff"));
        CordaFuture<SignedTransaction> future = bankOfGoodiesNode.startFlow(new GoodyTransferFlow(niceStuff, alice));
        mockNet.runNetwork();
        getOrThrow(future, null);
    }

    @Test(expected = InsufficientGoodiesException.class)
    public void transferWasComplete() {
        CordaFuture<SignedTransaction> future = bankOfGoodiesNode.startFlow(new GoodyTransferFlow(INITIAL_CANDY, alice));
        mockNet.runNetwork();
        Goody.State transfer = getOrThrow(future, null).getTx().outputsOfType(Goody.State.class).get(0);
        LOG.info("Transfer TX: output={}", transfer);

        Amount<Candy> moreCandy = new Amount<>(1, INITIAL_CANDY.getToken());
        CordaFuture<SignedTransaction> failFuture = bankOfGoodiesNode.startFlow(new GoodyTransferFlow(moreCandy, alice));
        mockNet.runNetwork();
        getOrThrow(failFuture, null);
    }

    @Test
    public void transferArrivedCorrectly() {
        Amount<Candy> candy = INITIAL_CANDY.splitEvenly(4).get(0);
        CordaFuture<SignedTransaction> toAliceFuture = bankOfGoodiesNode.startFlow(new GoodyTransferFlow(candy, alice));
        mockNet.runNetwork();
        Map<AbstractParty, List<Goody.State>> toAliceGoodies = byOwner(getOrThrow(toAliceFuture, null).getTx().outputsOfType(Goody.State.class));
        LOG.info("To Alice: output={}", toAliceGoodies);
        List<Goody.State> aliceGoodies = toAliceGoodies.get(alice);
        assertNotNull("Alice has received nothing", aliceGoodies);
        assertEquals(1, aliceGoodies.size());
        assertEquals(candy.getQuantity(), aliceGoodies.get(0).getAmount().getQuantity());
        assertEquals(candy.getToken(), aliceGoodies.get(0).getAmount().getToken().getProduct());

        CordaFuture<SignedTransaction> fromAliceFuture = aliceNode.startFlow(new GoodyTransferFlow(candy, bankOfGoodies));
        mockNet.runNetwork();
        Map<AbstractParty, List<Goody.State>> fromAliceGoodies = byOwner(getOrThrow(fromAliceFuture, null).getTx().outputsOfType(Goody.State.class));
        LOG.info("From Alice: output={}", fromAliceGoodies);
        List<Goody.State> bankGoodies = fromAliceGoodies.get(bankOfGoodies);
        assertNotNull("Bank has received nothing", bankGoodies);
        assertEquals(1, bankGoodies.size());
        assertEquals(candy.getQuantity(), bankGoodies.get(0).getAmount().getQuantity());
        assertEquals(candy.getToken(), bankGoodies.get(0).getAmount().getToken().getProduct());
    }

    @Test
    public void transferGeneratesChange() {
        Amount<Candy> candy = INITIAL_CANDY.splitEvenly(4).get(0);
        CordaFuture<SignedTransaction> toAliceFuture = bankOfGoodiesNode.startFlow(new GoodyTransferFlow(candy, alice));
        mockNet.runNetwork();
        Map<AbstractParty, List<Goody.State>> toAliceGoodies = byOwner(getOrThrow(toAliceFuture, null).getTx().outputsOfType(Goody.State.class));
        LOG.info("To Alice: output={}", toAliceGoodies);
        List<Goody.State> aliceGoodies = toAliceGoodies.get(alice);
        assertNotNull("Alice has received nothing", aliceGoodies);
        assertEquals(1, aliceGoodies.size());
        assertEquals(candy.getQuantity(), aliceGoodies.get(0).getAmount().getQuantity());
        assertEquals(candy.getToken(), aliceGoodies.get(0).getAmount().getToken().getProduct());

        List<Goody.State> bankChange = toAliceGoodies.get(bankOfGoodies);
        Amount<Candy> expectedChange = INITIAL_CANDY.minus(candy);
        assertNotNull("Bank has received no change", bankChange);
        assertEquals(1, bankChange.size());
        assertEquals(expectedChange.getQuantity(), bankChange.get(0).getAmount().getQuantity());
        assertEquals(expectedChange.getToken(), bankChange.get(0).getAmount().getToken().getProduct());
    }

    @Test
    public void totalCandyIsTransferred() {
        Amount<Candy> extraCandy = new Amount<>(10000, INITIAL_CANDY.getToken());
        CordaFuture<SignedTransaction> extraFuture = bankOfGoodiesNode.startFlow(new GoodyIssueFlow(extraCandy, GOODY_REFERENCE, mockNet.getDefaultNotaryIdentity()));
        getOrThrow(extraFuture, null);

        Amount<Candy> allCandy = INITIAL_CANDY.plus(extraCandy);
        CordaFuture<SignedTransaction> toAliceFuture = bankOfGoodiesNode.startFlow(new GoodyTransferFlow(allCandy, alice));
        mockNet.runNetwork();
        Map<AbstractParty, List<Goody.State>> toAliceGoodies = byOwner(getOrThrow(toAliceFuture, null).getTx().outputsOfType(Goody.State.class));
        LOG.info("To Alice: output={}", toAliceGoodies);
        List<Goody.State> aliceGoodies = toAliceGoodies.get(alice);
        assertNotNull("Alice has received nothing", aliceGoodies);
        assertEquals(1, aliceGoodies.size());
        assertEquals(allCandy.getQuantity(), aliceGoodies.get(0).getAmount().getQuantity());
        assertEquals(INITIAL_CANDY.getToken(), aliceGoodies.get(0).getAmount().getToken().getProduct());
        assertEquals(bankOfGoodies, aliceGoodies.get(0).getAmount().getToken().getIssuer().getParty());
        assertEquals(GOODY_REFERENCE, aliceGoodies.get(0).getAmount().getToken().getIssuer().getReference());
    }

    @Test
    public void testCandyCanBeTransferredIncrementally() {
        INITIAL_CANDY.splitEvenly(2).forEach(increment -> {
            CordaFuture<SignedTransaction> toAliceFuture = bankOfGoodiesNode.startFlow(new GoodyTransferFlow(increment, alice));
            mockNet.runNetwork();
            Map<AbstractParty, List<Goody.State>> toAliceGoodies = byOwner(getOrThrow(toAliceFuture, null).getTx().outputsOfType(Goody.State.class));
            LOG.info("To Alice: output={}", toAliceGoodies);
            List<Goody.State> aliceGoodies = toAliceGoodies.get(alice);
            assertNotNull("Alice has received nothing", aliceGoodies);
            assertEquals(1, aliceGoodies.size());
            assertEquals(increment.getQuantity(), aliceGoodies.get(0).getAmount().getQuantity());
            assertEquals(increment.getToken(), aliceGoodies.get(0).getAmount().getToken().getProduct());
        });
    }
}
