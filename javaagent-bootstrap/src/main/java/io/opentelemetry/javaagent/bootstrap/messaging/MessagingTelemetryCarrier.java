/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.messaging;

import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetryClaims;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignal;
import io.opentelemetry.instrumentation.api.internal.cache.Cache;
import javax.annotation.Nullable;

/**
 * The {@link MessagingTelemetryClaims} attached to a message, a record, or any other object that
 * carries one in-flight message.
 *
 * <p>Claims live here rather than in the {@link io.opentelemetry.context.Context} when the message
 * outlives the scope that emitted telemetry for it, or reaches the next instrumentation on a
 * different thread. A JMS message that already has a receive span, for instance, still has it when
 * a listener picks it up later.
 *
 * <p>The registry is keyed by object identity, so two instrumentations only meet here when they are
 * handling the same message, which is exactly when they have to agree on who emits what.
 *
 * <p>Claims are held weakly, so remembering them never keeps a message alive.
 */
public final class MessagingTelemetryCarrier {

  private static final Cache<Object, MessagingTelemetryClaims> carrierClaims = Cache.weak();

  /** Returns the claims held by {@code carrier}, or no claims when it is null or has none. */
  public static MessagingTelemetryClaims getClaims(@Nullable Object carrier) {
    if (carrier == null) {
      return MessagingTelemetryClaims.none();
    }
    MessagingTelemetryClaims claims = carrierClaims.get(carrier);
    return claims == null ? MessagingTelemetryClaims.none() : claims;
  }

  public static boolean isClaimed(
      @Nullable Object carrier, MessagingOperationType operation, MessagingTelemetrySignal signal) {
    return getClaims(carrier).contains(operation, signal);
  }

  public static void claim(
      @Nullable Object carrier, MessagingOperationType operation, MessagingTelemetrySignal signal) {
    if (carrier != null) {
      carrierClaims.put(carrier, getClaims(carrier).with(operation, signal));
    }
  }

  /**
   * Adds everything {@code source} holds to what {@code target} already holds, for an object that
   * wraps or continues another one without replacing what is known about it.
   */
  public static void merge(@Nullable Object source, @Nullable Object target) {
    MessagingTelemetryClaims claims = getClaims(source);
    if (target == null || claims.isEmpty()) {
      return;
    }
    carrierClaims.put(target, getClaims(target).union(claims));
  }

  /**
   * Makes {@code target} hold exactly what {@code source} holds, for an object that is being reused
   * or refilled and must not keep the claims of the message it carried before.
   */
  public static void replace(@Nullable Object source, @Nullable Object target) {
    if (target == null) {
      return;
    }
    MessagingTelemetryClaims claims = getClaims(source);
    if (claims.isEmpty()) {
      carrierClaims.remove(target);
    } else {
      carrierClaims.put(target, claims);
    }
  }

  /** Forgets every claim on {@code carrier}. */
  public static void clear(@Nullable Object carrier) {
    if (carrier != null) {
      carrierClaims.remove(carrier);
    }
  }

  private MessagingTelemetryCarrier() {}
}
