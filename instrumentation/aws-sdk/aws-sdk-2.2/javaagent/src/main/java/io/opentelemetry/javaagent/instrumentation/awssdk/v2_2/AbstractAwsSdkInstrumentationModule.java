/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v2_2;

import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;

abstract class AbstractAwsSdkInstrumentationModule extends InstrumentationModule {

  protected AbstractAwsSdkInstrumentationModule(String additionalInstrumentationName) {
    super("aws-sdk", "aws-sdk-2.2", additionalInstrumentationName);
  }

  @Override
  public boolean isHelperClass(String className) {
    return className.startsWith("io.opentelemetry.contrib.awsxray.");
  }
}
