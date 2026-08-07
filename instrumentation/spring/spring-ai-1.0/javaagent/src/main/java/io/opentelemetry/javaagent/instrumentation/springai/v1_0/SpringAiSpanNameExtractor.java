/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.springai.v1_0;

final class SpringAiSpanNameExtractor {
  static String name(SpringAiRequest request) {
    String model = request.model();
    return model == null ? "chat" : "chat " + model;
  }

  private SpringAiSpanNameExtractor() {}
}
