/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import org.apache.rocketmq.client.java.impl.producer.SendReceiptImpl;
import org.apache.rocketmq.client.java.message.PublishingMessageImpl;
import org.apache.rocketmq.shaded.com.google.common.util.concurrent.FutureCallback;

public final class SendSpanFinishingCallback implements FutureCallback<SendReceiptImpl> {
  private final Instrumenter<PublishingMessageImpl, SendReceiptImpl> instrumenter;
  private final Context context;
  private final PublishingMessageImpl message;

  public SendSpanFinishingCallback(
      Instrumenter<PublishingMessageImpl, SendReceiptImpl> instrumenter,
      Context context,
      PublishingMessageImpl message) {
    this.instrumenter = instrumenter;
    this.context = context;
    this.message = message;
  }

  @Override
  public void onSuccess(SendReceiptImpl sendReceipt) {
    instrumenter.end(context, message, sendReceipt, null);
  }

  @Override
  public void onFailure(Throwable t) {
    instrumenter.end(context, message, null, t);
  }
}
