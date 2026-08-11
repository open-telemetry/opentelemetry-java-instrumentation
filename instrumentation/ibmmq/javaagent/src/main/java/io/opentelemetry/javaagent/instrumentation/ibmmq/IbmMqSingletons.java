/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ibmmq;

import com.ibm.mq.MQQueueManager;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessageOperation;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import javax.annotation.Nullable;

class IbmMqSingletons {

  // Must be a pair of class literals: the javaagent's VirtualFieldFindRewriter rejects
  // any other form ("Type and field type must be class-literals"), so this cannot be
  // resolved reflectively via Class.forName.
  private static final VirtualField<MQQueueManager, String> queueManagerIdVirtualField =
      VirtualField.find(MQQueueManager.class, String.class);

  @Nullable private static Instrumenter<IbmMqRequest, IbmMqResponse> instrumenter;

  static VirtualField<MQQueueManager, String> queueManagerIdVirtualField() {
    return queueManagerIdVirtualField;
  }

  static Instrumenter<IbmMqRequest, IbmMqResponse> instrumenter() {
    if (instrumenter == null) {
      instrumenter = buildInstrumenter();
    }
    return instrumenter;
  }

  private static Instrumenter<IbmMqRequest, IbmMqResponse> buildInstrumenter() {
    InstrumenterBuilder<IbmMqRequest, IbmMqResponse> builder =
        Instrumenter.builder(
            GlobalOpenTelemetry.get(),
            "io.opentelemetry.ibmmq",
            IbmMqRequest::spanName);

    return builder
        .addAttributesExtractor(
            MessagingAttributesExtractor.create(
                new IbmMqMessagingAttributesGetter(), MessageOperation.PUBLISH))
        .addAttributesExtractor(new IbmMqAttributesExtractor())
        .buildInstrumenter();
  }

  private IbmMqSingletons() {}
}
