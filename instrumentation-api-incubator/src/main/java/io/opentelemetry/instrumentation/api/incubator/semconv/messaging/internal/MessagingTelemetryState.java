/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType;
import javax.annotation.Nullable;

/**
 * The {@link MessagingTelemetryClaims} held by a {@link Context}, used to coordinate messaging
 * telemetry between an operation and the messaging operations nested inside it.
 *
 * <p>Tracking is opt-in: a claim is only remembered once an instrumentation has called {@link
 * #enable(Context)} on the context it starts its nested work with. Two unrelated messaging
 * instrumentations that merely happen to nest therefore keep emitting their own telemetry, and only
 * a layer that knows it is coordinating with the layers below it takes ownership away from them.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class MessagingTelemetryState {

  private static final ContextKey<MessagingTelemetryClaims> MESSAGING_TELEMETRY_CLAIMS =
      ContextKey.named("messaging-telemetry-claims");

  /**
   * Returns a context that remembers messaging telemetry claims, so that operations nested inside
   * it can see what the outer operation already emits.
   */
  public static Context enable(Context context) {
    return isEnabled(context) ? context : context.with(MESSAGING_TELEMETRY_CLAIMS, claims(context));
  }

  public static boolean isEnabled(Context context) {
    return context.get(MESSAGING_TELEMETRY_CLAIMS) != null;
  }

  public static boolean isClaimed(
      Context context,
      @Nullable MessagingOperationType operation,
      MessagingTelemetrySignal signal) {
    return operation != null && claims(context).contains(operation, signal);
  }

  /** Claims a signal, whether or not this context remembers claims yet. */
  public static Context claim(
      Context context,
      @Nullable MessagingOperationType operation,
      MessagingTelemetrySignal signal) {
    if (operation == null) {
      return context;
    }
    return context.with(MESSAGING_TELEMETRY_CLAIMS, claims(context).with(operation, signal));
  }

  /**
   * Claims a signal only when this context remembers claims, so that an instrumentation which never
   * opted in does not take ownership away from the operations nested inside it.
   */
  public static Context claimIfEnabled(
      Context context,
      @Nullable MessagingOperationType operation,
      MessagingTelemetrySignal signal) {
    return isEnabled(context) ? claim(context, operation, signal) : context;
  }

  private static MessagingTelemetryClaims claims(Context context) {
    MessagingTelemetryClaims claims = context.get(MESSAGING_TELEMETRY_CLAIMS);
    return claims == null ? MessagingTelemetryClaims.none() : claims;
  }

  private MessagingTelemetryState() {}
}
