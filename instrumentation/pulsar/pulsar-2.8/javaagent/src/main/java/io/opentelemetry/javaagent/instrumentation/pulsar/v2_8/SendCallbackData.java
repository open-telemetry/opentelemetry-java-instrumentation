/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8;

import static io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry.PulsarSingletons.producerInstrumenter;
import static java.util.Objects.requireNonNull;
import static java.util.logging.Level.FINE;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry.PulsarRequest;
import java.lang.ref.WeakReference;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.impl.SendCallback;

public class SendCallbackData {
  private static final Logger logger = Logger.getLogger(SendCallbackData.class.getName());
  private static final ContextKey<SendCallbackData> KEY =
      ContextKey.named("opentelemetry-pulsar-send");
  private static final ContextKey<Boolean> CALLBACK_COMPLETION_KEY =
      ContextKey.named("opentelemetry-pulsar-callback-completion");
  private static final ContextKey<Boolean> SUPPRESSED_SEND_KEY =
      ContextKey.named("opentelemetry-pulsar-suppressed-send");

  private final Context parent;
  public final Context context;
  private final AtomicBoolean ended = new AtomicBoolean();
  private boolean internalSendAsyncPending;
  @Nullable private CompletableFuture<MessageId> sendFuture;
  @Nullable private WeakReference<SendCallback> callback;
  public PulsarRequest request;
  private final MessageAssociation messageAssociation;

  public static SendCallbackData create(Context parent, Context context, PulsarRequest request) {
    return new SendCallbackData(parent, context, request, false);
  }

  public static SendCallbackData createForInternalSend(
      Context parent, Context context, PulsarRequest request) {
    return new SendCallbackData(parent, context, request, true);
  }

  private SendCallbackData(
      Context parent, Context context, PulsarRequest request, boolean internalSendAsyncPending) {
    this.parent = parent;
    this.context = context;
    this.request = request;
    this.internalSendAsyncPending = internalSendAsyncPending;
    this.messageAssociation = new MessageAssociation(this);
  }

  MessageAssociation messageAssociation() {
    return messageAssociation;
  }

  public boolean claimInternalSendAsync() {
    if (!internalSendAsyncPending) {
      return false;
    }
    internalSendAsyncPending = false;
    return true;
  }

  @Nullable
  public static SendCallbackData current() {
    return Context.current().get(KEY);
  }

  public Context invocationContext() {
    return context.with(KEY, this);
  }

  public Context callbackCompletionContext() {
    return invocationContext().with(CALLBACK_COMPLETION_KEY, true);
  }

  static boolean isCallbackCompletion() {
    return Boolean.TRUE.equals(Context.current().get(CALLBACK_COMPLETION_KEY));
  }

  public Context preparationContext() {
    return parent.with(KEY, this);
  }

  void associateCallback(SendCallback callback) {
    this.callback = new WeakReference<>(callback);
  }

  @Nullable
  SendCallback callback() {
    return callback == null ? null : callback.get();
  }

  Context parentContext() {
    return parent;
  }

  public static Context withoutOperation(Context context) {
    return context.with(KEY, null).with(SUPPRESSED_SEND_KEY, true);
  }

  public static Context callerContext(Context context) {
    return context.with(KEY, null).with(SUPPRESSED_SEND_KEY, null);
  }

  public static boolean isSendSuppressed() {
    return Boolean.TRUE.equals(Context.current().get(SUPPRESSED_SEND_KEY));
  }

  static final class MessageAssociation {
    private final WeakReference<SendCallbackData> data;

    private MessageAssociation(SendCallbackData data) {
      this.data = new WeakReference<>(data);
    }

    @Nullable
    SendCallbackData get() {
      return data.get();
    }
  }

  public void end(@Nullable Throwable error) {
    if (ended.compareAndSet(false, true)) {
      try {
        producerInstrumenter().end(context, request, null, error);
      } catch (Throwable t) {
        logger.log(FINE, "Failed to end Pulsar send", t);
      } finally {
        try {
          VirtualFieldStore.clearSendCallbackData(this);
        } catch (Throwable t) {
          logger.log(FINE, "Failed to clear Pulsar send state", t);
        }
      }
    }
  }

  public CompletableFuture<MessageId> wrap(CompletableFuture<MessageId> future) {
    if (sendFuture != null) {
      return sendFuture;
    }
    CompletableFuture<MessageId> result = new CompletableFuture<>();
    future.whenComplete(
        (messageId, error) -> {
          try {
            end(error);
          } finally {
            try (Scope ignored = parent.makeCurrent()) {
              if (error == null) {
                result.complete(messageId);
              } else {
                result.completeExceptionally(error);
              }
            }
          }
        });
    return result;
  }

  public CompletableFuture<MessageId> createSendFuture() {
    CompletableFuture<MessageId> future = new SendFuture(this);
    sendFuture = future;
    return future;
  }

  private static class SendFuture extends CompletableFuture<MessageId> {
    @Nullable private SendCallbackData data;

    SendFuture(SendCallbackData data) {
      this.data = data;
    }

    @Override
    public boolean complete(MessageId messageId) {
      return finish(messageId, null);
    }

    @Override
    public boolean completeExceptionally(Throwable error) {
      return finish(null, requireNonNull(error));
    }

    private boolean finish(@Nullable MessageId messageId, @Nullable Throwable error) {
      SendCallbackData callbackData = data;
      if (callbackData == null || !SendCallbackData.isCallbackCompletion()) {
        return error == null ? super.complete(messageId) : super.completeExceptionally(error);
      }
      data = null;
      callbackData.end(error);
      try (Scope ignored = callbackData.parent.makeCurrent()) {
        return error == null ? super.complete(messageId) : super.completeExceptionally(error);
      }
    }
  }
}
