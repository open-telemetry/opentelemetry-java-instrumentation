/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.javaagent.bootstrap.CallDepth;
import java.util.Collection;
import org.junit.jupiter.api.Test;

class QueryExecutorInstrumentationTest {

  @Test
  void restoresCallDepthWhenStartThrows() {
    Object queryExecutor = new Object();
    Collection<?> batch = mock(Collection.class);
    RuntimeException failure = new RuntimeException("failure");
    when(batch.size()).thenThrow(failure);

    assertThatThrownBy(
            () ->
                QueryExecutorInstrumentation.QueryAdvice.AdviceScope.start(
                    queryExecutor,
                    new Object(),
                    null,
                    "executeBatchQuery",
                    new Object[] {"SELECT 1", batch}))
        .isSameAs(failure);

    CallDepth callDepth = CallDepth.forClass(queryExecutor.getClass());
    assertThat(callDepth.getAndIncrement()).isZero();
    assertThat(callDepth.decrementAndGet()).isZero();
  }
}
