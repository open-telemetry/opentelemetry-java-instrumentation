/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq.v2_7;

import com.rabbitmq.client.Connection;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.util.function.Function;
import javax.annotation.Nullable;

class RabbitConnectionAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  private final Function<REQUEST, Connection> connectionExtractor;

  RabbitConnectionAttributesExtractor(Function<REQUEST, Connection> connectionExtractor) {
    this.connectionExtractor = connectionExtractor;
  }

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {
    RabbitConnectionAttributes.apply(attributes, connectionExtractor.apply(request));
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {}
}
