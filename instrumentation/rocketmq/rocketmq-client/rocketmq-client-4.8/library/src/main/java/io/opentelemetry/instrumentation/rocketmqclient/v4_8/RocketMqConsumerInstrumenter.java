/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.List;
import org.apache.rocketmq.common.message.MessageExt;

final class RocketMqConsumerInstrumenter {

  private final Instrumenter<MessageExt, Void> singleProcessInstrumenter;
  private final Instrumenter<MessageExt, Void> batchProcessInstrumenter;
  private final Instrumenter<Void, Void> batchReceiveInstrumenter;

  RocketMqConsumerInstrumenter(
      Instrumenter<MessageExt, Void> singleProcessInstrumenter,
      Instrumenter<MessageExt, Void> batchProcessInstrumenter,
      Instrumenter<Void, Void> batchReceiveInstrumenter) {
    this.singleProcessInstrumenter = singleProcessInstrumenter;
    this.batchProcessInstrumenter = batchProcessInstrumenter;
    this.batchReceiveInstrumenter = batchReceiveInstrumenter;
  }

  Context start(Context parentContext, List<MessageExt> msgs) {
    if (msgs.size() == 1) {
      if (singleProcessInstrumenter.shouldStart(parentContext, msgs.get(0))) {
        return singleProcessInstrumenter.start(parentContext, msgs.get(0));
      }
    } else {
      if (batchReceiveInstrumenter.shouldStart(parentContext, null)) {
        Context rootContext = batchReceiveInstrumenter.start(parentContext, null);
        for (MessageExt message : msgs) {
          createChildSpan(rootContext, message);
        }
        return rootContext;
      }
    }
    return parentContext;
  }

  private void createChildSpan(Context parentContext, MessageExt msg) {
    if (batchProcessInstrumenter.shouldStart(parentContext, msg)) {
      Context context = batchProcessInstrumenter.start(parentContext, msg);
      batchProcessInstrumenter.end(context, msg, null, null);
    }
  }

  void end(Context context, List<MessageExt> msgs) {
    if (msgs.size() == 1) {
      singleProcessInstrumenter.end(context, msgs.get(0), null, null);
    } else {
      batchReceiveInstrumenter.end(context, null, null, null);
    }
  }
}
