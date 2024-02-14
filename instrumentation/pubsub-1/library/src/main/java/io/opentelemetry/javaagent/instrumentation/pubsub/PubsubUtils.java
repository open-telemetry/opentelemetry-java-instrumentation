/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pubsub;

public class PubsubUtils {
  private PubsubUtils() {}

  public static final String INSTRUMENTATION_NAME = "io.opentelemetry.pubsub-1";

  public static String getSpanName(String operation, String subject) {
    return String.format("%s %s", subject, operation);
  }

  public static String getResourceName(String resourcePath) {
    return resourcePath.substring(resourcePath.lastIndexOf("/") + 1);
  }

  public static String getFullResourceName(String resourcePath) {
    return String.format("//pubsub.googleapis.com/%s", resourcePath);
  }
}
