/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmq;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.List;
import org.apache.rocketmq.common.message.MessageExt;

final class RocketMqConsumerInstrumenter {

  private final Instrumenter<MessageExt, MessageExt> singleProcessInstrumenter;
  private final Instrumenter<MessageExt, MessageExt> batchProcessInstrumenter;
  private final Instrumenter<Void, Void> receiveInstrumenter;

  RocketMqConsumerInstrumenter(
      Instrumenter<MessageExt, MessageExt> singleProcessInstrumenter,
      Instrumenter<MessageExt, MessageExt> batchProcessInstrumenter,
      Instrumenter<Void, Void> receiveInstrumenter) {
    this.singleProcessInstrumenter = singleProcessInstrumenter;
    this.batchProcessInstrumenter = batchProcessInstrumenter;
    this.receiveInstrumenter = receiveInstrumenter;
  }

  Context startSpan(Context parentContext, List<MessageExt> msgs) {
    if (msgs.size() == 1) {
      return singleProcessInstrumenter.start(parentContext, msgs.get(0));
    } else {
      Context rootContext = receiveInstrumenter.start(parentContext, null);
      for (MessageExt message : msgs) {
        createChildSpan(rootContext, message);
      }
      return rootContext;
    }
  }

  private void createChildSpan(Context parentContext, MessageExt msg) {
    Context context = batchProcessInstrumenter.start(parentContext, msg);
    batchProcessInstrumenter.end(context, msg, null, null);
  }

  void end(Context context, List<MessageExt> msgs) {
    if (msgs.size() == 1) {
      singleProcessInstrumenter.end(context, msgs.get(0), null, null);
    } else {
      receiveInstrumenter.end(context, null, null, null);
    }
  }
}
