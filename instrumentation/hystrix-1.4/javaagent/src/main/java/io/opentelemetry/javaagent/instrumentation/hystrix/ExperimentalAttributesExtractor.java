/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hystrix;

import static io.opentelemetry.api.common.AttributeKey.booleanKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;

final class ExperimentalAttributesExtractor implements AttributesExtractor<HystrixRequest, Void> {
  private static final AttributeKey<String> HYSTRIX_COMMAND = stringKey("hystrix.command");
  private static final AttributeKey<String> HYSTRIX_GROUP = stringKey("hystrix.group");
  private static final AttributeKey<Boolean> HYSTRIX_CIRCUIT_OPEN =
      booleanKey("hystrix.circuit_open");

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, HystrixRequest hystrixRequest) {
    String commandName = hystrixRequest.command().getCommandKey().name();
    String groupName = hystrixRequest.command().getCommandGroup().name();
    boolean circuitOpen = hystrixRequest.command().isCircuitBreakerOpen();

    attributes.put(HYSTRIX_COMMAND, commandName);
    attributes.put(HYSTRIX_GROUP, groupName);
    attributes.put(HYSTRIX_CIRCUIT_OPEN, circuitOpen);
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      HystrixRequest hystrixRequest,
      @Nullable Void unused,
      @Nullable Throwable error) {}
}
