/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.v5_3;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.annotation.support.async.AsyncOperationEndStrategies;
import io.opentelemetry.instrumentation.api.annotation.support.async.AsyncOperationEndStrategy;
import io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Mono;

class SpringWebfluxClientTelemetryTest {

  private AsyncOperationEndStrategies originalStrategies;
  private AsyncOperationEndStrategy originalMonoStrategy;
  private RecordingAsyncOperationEndStrategies recordingStrategies;

  @BeforeEach
  @AfterEach
  void resetReactorHook() {
    ContextPropagationOperator.create().resetOnEachOperator();
    if (recordingStrategies != null) {
      AsyncOperationEndStrategies.internalSetStrategiesStorage(originalStrategies);
      recordingStrategies.unregisterRegisteredStrategies();
      assertThat(originalStrategies.resolveStrategy(Mono.class)).isSameAs(originalMonoStrategy);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void registersReactorHookOnlyWhenRequested(boolean registerHook) {
    SpringWebfluxClientTelemetry telemetry =
        SpringWebfluxClientTelemetry.create(OpenTelemetry.noop());
    if (registerHook) {
      originalStrategies = AsyncOperationEndStrategies.instance();
      originalMonoStrategy = originalStrategies.resolveStrategy(Mono.class);
      recordingStrategies = new RecordingAsyncOperationEndStrategies(originalStrategies);
      AsyncOperationEndStrategies.internalSetStrategiesStorage(recordingStrategies);
      telemetry.createFilterAndRegisterReactorHook();
      assertThat(recordingStrategies.registeredStrategies).hasSize(1);
    } else {
      telemetry.createFilter();
    }

    ContextKey<String> key = ContextKey.named("test");
    Mono<Boolean> hasContext =
        Mono.just(1).map(unused -> "value".equals(Context.current().get(key)));
    assertThat(
            ContextPropagationOperator.runWithContext(hasContext, Context.root().with(key, "value"))
                .block())
        .isEqualTo(registerHook);
  }

  private static final class RecordingAsyncOperationEndStrategies
      extends AsyncOperationEndStrategies {

    private final AsyncOperationEndStrategies delegate;
    private final List<AsyncOperationEndStrategy> registeredStrategies = new ArrayList<>();
    private final Thread registeringThread = Thread.currentThread();

    private RecordingAsyncOperationEndStrategies(AsyncOperationEndStrategies delegate) {
      this.delegate = delegate;
    }

    @Override
    public void registerStrategy(AsyncOperationEndStrategy strategy) {
      if (Thread.currentThread() == registeringThread) {
        registeredStrategies.add(strategy);
      }
      delegate.registerStrategy(strategy);
    }

    @Override
    public void unregisterStrategy(AsyncOperationEndStrategy strategy) {
      delegate.unregisterStrategy(strategy);
    }

    @Override
    public AsyncOperationEndStrategy resolveStrategy(Class<?> returnType) {
      return delegate.resolveStrategy(returnType);
    }

    private void unregisterRegisteredStrategies() {
      registeredStrategies.forEach(delegate::unregisterStrategy);
    }
  }
}
