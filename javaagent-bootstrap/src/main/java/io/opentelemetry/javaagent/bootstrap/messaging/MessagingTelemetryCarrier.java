/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.messaging;

import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetryClaims;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignal;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import javax.annotation.Nullable;

/**
 * Accesses the {@link MessagingTelemetryClaims} attached to objects that carry in-flight messages.
 *
 * <p>Claims live here rather than in the {@link io.opentelemetry.context.Context} when the message
 * outlives the scope that emitted telemetry for it, or reaches the next instrumentation on a
 * different thread. A JMS message that already has a receive span, for instance, still has it when
 * a listener picks it up later.
 *
 * <p>Instrumentations that coordinate through a carrier must create accessors with the same virtual
 * field type pair.
 */
public final class MessagingTelemetryCarrier<T> {

  private final VirtualField<T, MessagingTelemetryClaims> claimsField;

  public static <T> MessagingTelemetryCarrier<T> create(
      VirtualField<T, MessagingTelemetryClaims> claimsField) {
    return new MessagingTelemetryCarrier<>(claimsField);
  }

  private MessagingTelemetryCarrier(VirtualField<T, MessagingTelemetryClaims> claimsField) {
    this.claimsField = claimsField;
  }

  /** Returns the claims held by {@code carrier}, or no claims when it is null or has none. */
  public MessagingTelemetryClaims getClaims(@Nullable T carrier) {
    if (carrier == null) {
      return MessagingTelemetryClaims.none();
    }
    MessagingTelemetryClaims claims = claimsField.get(carrier);
    return claims == null ? MessagingTelemetryClaims.none() : claims;
  }

  public boolean isClaimed(
      @Nullable T carrier, MessagingOperationType operation, MessagingTelemetrySignal signal) {
    return getClaims(carrier).contains(operation, signal);
  }

  public void claim(
      @Nullable T carrier, MessagingOperationType operation, MessagingTelemetrySignal signal) {
    if (carrier != null) {
      synchronized (carrier) {
        claimsField.set(carrier, getClaims(carrier).with(operation, signal));
      }
    }
  }

  /**
   * Adds everything {@code source} holds to what {@code target} already holds, for an object that
   * wraps or continues another one without replacing what is known about it.
   */
  public <S> void mergeFrom(
      MessagingTelemetryCarrier<S> sourceCarrier, @Nullable S source, @Nullable T target) {
    MessagingTelemetryClaims claims = sourceCarrier.getClaims(source);
    if (target == null || claims.isEmpty()) {
      return;
    }
    synchronized (target) {
      claimsField.set(target, getClaims(target).union(claims));
    }
  }

  /**
   * Makes {@code target} hold exactly what {@code source} holds, for an object that is being reused
   * or refilled and must not keep the claims of the message it carried before.
   */
  public <S> void replaceFrom(
      MessagingTelemetryCarrier<S> sourceCarrier, @Nullable S source, @Nullable T target) {
    if (target == null) {
      return;
    }
    MessagingTelemetryClaims claims = sourceCarrier.getClaims(source);
    synchronized (target) {
      if (claims.isEmpty()) {
        claimsField.set(target, null);
      } else {
        claimsField.set(target, claims);
      }
    }
  }

  /** Forgets every claim on {@code carrier}. */
  public void clear(@Nullable T carrier) {
    if (carrier != null) {
      synchronized (carrier) {
        claimsField.set(carrier, null);
      }
    }
  }
}
