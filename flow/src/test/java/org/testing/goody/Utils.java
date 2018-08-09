package org.testing.goody;

import net.corda.core.contracts.OwnableState;
import net.corda.core.identity.AbstractParty;

import javax.validation.constraints.NotNull;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

public final class Utils {
    private Utils() {}

    public static <T extends OwnableState> Map<AbstractParty, List<T>> byOwner(@NotNull Collection<T> states) {
        return states.stream().collect(groupingBy(OwnableState::getOwner, toList()));
    }
}
