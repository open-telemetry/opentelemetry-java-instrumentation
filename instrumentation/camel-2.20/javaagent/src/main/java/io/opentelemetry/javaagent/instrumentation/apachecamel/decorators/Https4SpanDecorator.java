/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel.decorators;

class Https4SpanDecorator extends Http4SpanDecorator {
  @Override
  protected String getProtocol() {
    return "https4";
  }
}
