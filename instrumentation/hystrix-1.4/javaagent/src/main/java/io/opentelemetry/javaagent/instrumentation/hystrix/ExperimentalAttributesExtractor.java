/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hystrix;

import static io.opentelemetry.api.common.AttributeKey.booleanKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import org.checkerframework.checker.nullness.qual.Nullable;

final class ExperimentalAttributesExtractor extends AttributesExtractor<HystrixRequest, Void> {
  private static final AttributeKey<String> HYSTRIX_COMMAND = stringKey("hystrix.command");
  private static final AttributeKey<String> HYSTRIX_GROUP = stringKey("hystrix.group");
  private static final AttributeKey<Boolean> HYSTRIX_CIRCUIT_OPEN =
      booleanKey("hystrix.circuit_open");

  @Override
  protected void onStart(AttributesBuilder attributes, HystrixRequest hystrixRequest) {
    String commandName = hystrixRequest.command().getCommandKey().name();
    String groupName = hystrixRequest.command().getCommandGroup().name();
    boolean circuitOpen = hystrixRequest.command().isCircuitBreakerOpen();

    set(attributes, HYSTRIX_COMMAND, commandName);
    set(attributes, HYSTRIX_GROUP, groupName);
    set(attributes, HYSTRIX_CIRCUIT_OPEN, circuitOpen);
  }

  @Override
  protected void onEnd(
      AttributesBuilder attributes,
      HystrixRequest hystrixRequest,
      @Nullable Void unused,
      @Nullable Throwable error) {}
}
