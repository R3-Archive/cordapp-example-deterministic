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
import org.testing.goody.flows.GoodyTransferFlow;

import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;
import static net.corda.core.utilities.KotlinUtilsKt.getOrThrow;
import static net.corda.testing.core.TestConstants.*;
import static net.corda.testing.core.TestUtils.singleIdentity;
import static org.junit.Assert.*;
import static org.testing.goody.Constants.*;
import static org.testing.goody.Utils.byOwner;

public class GoodyMultiIssuerJavaTest {
    private static final Logger LOG = LoggerFactory.getLogger(GoodyMultiIssuerJavaTest.class);
    private static final String CANDY_TYPE = "Nougat";
    private static final Amount<Candy> BANK_CANDY = new Amount<>(7684, new Candy(CANDY_TYPE));
    private static final Amount<Candy> SHOP_CANDY = new Amount<>(963, new Candy(CANDY_TYPE));
    private static final OpaqueBytes GOODY_BANK_REFERENCE = new OpaqueBytes(new byte[] { 0x65, 0x33, 0x00, 0x24, 0x7C, 0x69 });
    private static final OpaqueBytes GOODY_SHOP_REFERENCE = new OpaqueBytes(new byte[] { 0x7E, 0x50, 0x3F, 0x18, 0x0E, 0x11 });

    private MockNetwork mockNet;
    private StartedMockNode bankOfGoodiesNode;
    private StartedMockNode shopOfGoodiesNode;
    private StartedMockNode aliceNode;
    private Party bankOfGoodies;
    private Party shopOfGoodies;
    private Party alice;
    private Party bob;

    @Before
    public void setup() {
        mockNet = new MockNetwork(
            asList("org.testing.goody.contracts", "org.testing.goody.schemas")
        );
        bankOfGoodiesNode = mockNet.createPartyNode(BOG_NAME);
        bankOfGoodies = bankOfGoodiesNode.getInfo().identityFromX500Name(BOG_NAME);
        shopOfGoodiesNode = mockNet.createPartyNode(SOG_NAME);
        shopOfGoodies = shopOfGoodiesNode.getInfo().identityFromX500Name(SOG_NAME);
        aliceNode = mockNet.createPartyNode(ALICE_NAME);
        alice = singleIdentity(aliceNode.getInfo());
        bob = singleIdentity(mockNet.createPartyNode(BOB_NAME).getInfo());

        CordaFuture<SignedTransaction> bankFuture = bankOfGoodiesNode.startFlow(new GoodyIssueFlow(BANK_CANDY, GOODY_BANK_REFERENCE, mockNet.getDefaultNotaryIdentity()));
        getOrThrow(bankFuture, null);
        CordaFuture<SignedTransaction> shopFuture = shopOfGoodiesNode.startFlow(new GoodyIssueFlow(SHOP_CANDY, GOODY_SHOP_REFERENCE, mockNet.getDefaultNotaryIdentity()));
        getOrThrow(shopFuture, null);
    }

    @After
    public void cleanUp() {
        mockNet.stopNodes();
    }

    @Test
    public void checkTransferCandyFromAllIssuers() {
        CordaFuture<SignedTransaction> fromBankFuture = bankOfGoodiesNode.startFlow(new GoodyTransferFlow(BANK_CANDY, alice));
        CordaFuture<SignedTransaction> fromShopFuture = shopOfGoodiesNode.startFlow(new GoodyTransferFlow(SHOP_CANDY, alice));
        mockNet.runNetwork();
        Goody.State bankTransfer = getOrThrow(fromBankFuture, null).getTx().outputsOfType(Goody.State.class).get(0);
        LOG.info("From Bank To Alice TX: output={}", bankTransfer);
        Goody.State shopTransfer = getOrThrow(fromShopFuture, null).getTx().outputsOfType(Goody.State.class).get(0);
        LOG.info("From Shop To Alice TX: output={}", shopTransfer);

        CordaFuture<SignedTransaction> toBobFuture = aliceNode.startFlow(new GoodyTransferFlow(BANK_CANDY.plus(SHOP_CANDY), bob));
        mockNet.runNetwork();
        Map<AbstractParty, List<Goody.State>> toBobGoodies = byOwner(getOrThrow(toBobFuture, null).getTx().outputsOfType(Goody.State.class));
        LOG.info("From Alice To Bob: output={}", toBobGoodies);
        assertEquals(1, toBobGoodies.size());

        List<Goody.State> bobGoodies = toBobGoodies.get(bob);
        assertNotNull("Bob has no goodies", bobGoodies);
        assertEquals(2, bobGoodies.size());

        Goody.State bankGoody = bobGoodies.stream().filter(it -> it.isIssuedBy(bankOfGoodies)).findFirst().orElse(null);
        assertNotNull("Bank has no goodies", bankGoody);
        assertEquals(BANK_CANDY.getQuantity(), bankGoody.getAmount().getQuantity());

        Goody.State shopGoody = bobGoodies.stream().filter(it -> it.isIssuedBy(shopOfGoodies)).findFirst().orElse(null);
        assertNotNull("Shop has no goodies", shopGoody);
        assertEquals(SHOP_CANDY.getQuantity(), shopGoody.getAmount().getQuantity());
    }
}
