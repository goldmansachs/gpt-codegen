package org.rj.modelgen.llm.state;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.rj.modelgen.llm.statemodel.data.common.StandardModelData;
import org.rj.modelgen.llm.statemodel.signals.common.StandardSignals;
import org.rj.modelgen.llm.statemodel.states.common.ExecuteLogic;
import org.rj.modelgen.llm.util.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class ModelInterfaceStateMachineTest {
    private static final Logger LOG = LoggerFactory.getLogger(ModelInterfaceStateMachineTest.class);

    @Test
    public void testBasicModelConfiguration() throws Exception {
        final var model = createBasicModel();

        final var layout = model.getLayoutInfo();

        Assertions.assertEquals(Set.of(
                new ModelInterfaceTransitionRule.Reference("A", "Success", "B"),
                new ModelInterfaceTransitionRule.Reference("B", "Success", "C")
        ), layout);
    }

    @Test
    public void testInsertStatesInExistingModel() throws Exception {
        final var model = createBasicModel()
                .withModelCustomization(modelData -> {
                    final var newStateX = testState("X");
                    final var newStateY = testState("Y");

                    return new ModelInterfaceStateMachineCustomization()
                            .withNewStateInsertedAfter(newStateX, "A")
                            .withNewStateInsertedAfter(newStateY, "B");
                });

        final var layout = model.getLayoutInfo();

        Assertions.assertEquals(Set.of(
                new ModelInterfaceTransitionRule.Reference("A", "Success", "X"),
                new ModelInterfaceTransitionRule.Reference("X", "Success", "B"),
                new ModelInterfaceTransitionRule.Reference("B", "Success", "Y"),
                new ModelInterfaceTransitionRule.Reference("Y", "Success", "C")
        ), layout);
    }

    @Test
    public void testCancellationStopsExecutionBeforeNextState() throws Exception {
        final CancellationRequest token = new CancellationRequest();
        final AtomicInteger stateAInvocations = new AtomicInteger();
        final AtomicInteger stateBInvocations = new AtomicInteger();

        final ModelInterfaceState stateA = new ExecuteLogic() {
            @Override
            protected Mono<Result<Void, String>> executeLogic() {
                stateAInvocations.incrementAndGet();
                token.cancel();
                return Mono.just(Result.Ok());
            }
        }.withOverriddenId("A");

        final ModelInterfaceState stateB = new ExecuteLogic() {
            @Override
            protected Mono<Result<Void, String>> executeLogic() {
                stateBInvocations.incrementAndGet();
                return Mono.just(Result.Ok());
            }
        }.withOverriddenId("B");

        final var states = List.of(stateA, stateB);
        final var rules = new ModelInterfaceTransitionRules(List.of(testRule(stateA, stateB)));
        final var model = new ModelInterfaceStateMachine(ModelInterfaceStateMachine.class, null, states, rules);

        final var payload = new ModelInterfaceInputPayload("session-1", "request", null);
        payload.put(StandardModelData.Cancel, token);

        final var result = model.execute("A", StandardSignals.SUCCESS, payload).block();

        Assertions.assertEquals(1, stateAInvocations.get());
        Assertions.assertEquals(0, stateBInvocations.get());
        Assertions.assertInstanceOf(ModelInterfaceStandardStates.CANCELLED.class, result.getResult());
        Assertions.assertTrue(result.isCancelled());
        Assertions.assertFalse(result.isSuccessful());
    }

    private ModelInterfaceStateMachine createBasicModel() {
        final var states = List.of(testState("A"), testState("B"), testState("C"));
        final var rules = new ModelInterfaceTransitionRules(List.of(
                testRule(states.get(0), states.get(1)),
                testRule(states.get(1), states.get(2))
        ));

        return new ModelInterfaceStateMachine(ModelInterfaceStateMachine.class, null, states, rules);
    }

    private ModelInterfaceState testState(String id) {
        return new ExecuteLogic() {
            @Override
            protected Mono<Result<Void, String>> executeLogic() {
                LOG.info("Executing node {}", getId());
                return Mono.just(Result.Ok());
            }
        }
        .withOverriddenId(id);
    }

    private ModelInterfaceTransitionRule testRule(ModelInterfaceState from, ModelInterfaceState to) {
        return testRule(from, to, StandardSignals.SUCCESS);
    }

    private ModelInterfaceTransitionRule testRule(ModelInterfaceState from, ModelInterfaceState to, String signalType) {
        return new ModelInterfaceTransitionRule(from, signalType, to);
    }

}
