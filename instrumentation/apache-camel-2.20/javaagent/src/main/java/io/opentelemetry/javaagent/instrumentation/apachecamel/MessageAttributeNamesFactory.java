/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel;

import io.opentelemetry.api.GlobalOpenTelemetry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;
import org.apache.camel.component.aws.sqs.SqsEndpoint;

final class MessageAttributeNamesFactory {

  private MessageAttributeNamesFactory() {}

  public static String withTextMapPropagatorFields(SqsEndpoint sqsEndpoint) {
    String attributeNames = sqsEndpoint.getConfiguration().getMessageAttributeNames();

    Collection<String> fields =
        new ArrayList<>(GlobalOpenTelemetry.get().getPropagators().getTextMapPropagator().fields());
    if (attributeNames != null && !attributeNames.trim().isEmpty()) {
      fields.add(attributeNames);
    }
    return fields.stream().collect(Collectors.joining(","));
  }
}
