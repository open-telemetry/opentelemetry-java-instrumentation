/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.common.v1_1;

import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType.PROCESS;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignal.PROCESS_DURATION;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetryState;
import io.opentelemetry.instrumentation.api.instrumenter.OperationListener;
import org.junit.jupiter.api.Test;

class JmsProcessMetricsTest {

  private static final ContextKey<String> APPLICATION_KEY = ContextKey.named("application-key");

  @Test
  void recordsDurationWithoutAmbientProcessMetricState() {
    RecordingListener duration = new RecordingListener();
    OperationListener listener = JmsProcessMetrics.create(duration);
    Context ambientContext =
        MessagingTelemetryState.add(
            Context.root().with(APPLICATION_KEY, "application-value"), PROCESS, PROCESS_DURATION);
    Attributes startAttributes = Attributes.builder().put("start", "value").build();
    Attributes endAttributes = Attributes.builder().put("end", "value").build();

    Context context = listener.onStart(ambientContext, startAttributes, 10);
    listener.onEnd(context, endAttributes, 20);

    assertThat(MessagingTelemetryState.contains(duration.startContext, PROCESS, PROCESS_DURATION))
        .isFalse();
    assertThat(MessagingTelemetryState.contains(context, PROCESS, PROCESS_DURATION)).isTrue();
    assertThat(duration.recordings).isEqualTo(1);
    assertThat(context.get(APPLICATION_KEY)).isEqualTo("application-value");
  }

  private static final class RecordingListener implements OperationListener {
    private Context startContext;
    private int recordings;

    @Override
    public Context onStart(Context context, Attributes startAttributes, long startNanos) {
      startContext = context;
      return context;
    }

    @Override
    public void onEnd(Context context, Attributes endAttributes, long endNanos) {
      recordings++;
    }
  }
}
