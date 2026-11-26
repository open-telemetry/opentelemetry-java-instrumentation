/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redissonmetrics.common.v2_3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.opentelemetry.api.metrics.BatchCallback;
import io.opentelemetry.javaagent.instrumentation.redissonmetrics.common.v2_3.RedissonConnectionPoolMetrics.Registration;
import org.junit.jupiter.api.Test;

class RedissonConnectionPoolMetricsTest {

  @Test
  void closesCallbacksOutsideMonitorOnceEvenWhenReentered() {
    BatchCallback regular = mock(BatchCallback.class);
    BatchCallback subscription = mock(BatchCallback.class);
    Registration registration = new Registration(regular, subscription);
    doAnswer(
            invocation -> {
              assertThat(Thread.holdsLock(registration)).isFalse();
              registration.close();
              return null;
            })
        .when(regular)
        .close();
    doAnswer(
            invocation -> {
              assertThat(Thread.holdsLock(registration)).isFalse();
              return null;
            })
        .when(subscription)
        .close();

    registration.close();
    registration.close();

    verify(regular).close();
    verify(subscription).close();
  }

  @Test
  void closesSubscriptionWithoutRegularCallback() {
    BatchCallback subscription = mock(BatchCallback.class);
    Registration registration = new Registration(null, subscription);

    registration.close();
    registration.close();

    verify(subscription).close();
  }
}
