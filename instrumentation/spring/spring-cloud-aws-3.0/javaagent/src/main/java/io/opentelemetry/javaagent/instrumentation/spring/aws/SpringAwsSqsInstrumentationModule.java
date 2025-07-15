/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.aws;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class SpringAwsSqsInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {

  public SpringAwsSqsInstrumentationModule() {
    super("spring-cloud-aws", "spring-cloud-aws-3.0");
  }

  @Override
  public boolean isHelperClass(String className) {
    return className.startsWith("io.opentelemetry.contrib.awsxray.");
  }

  @Override
  public String getModuleGroup() {
    return "aws-sdk-v2";
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new AbstractMessageConvertingMessageSourceInstrumentation(),
        new MessagingMessageListenerAdapterInstrumentation(),
        new SqsTemplateInstrumentation(),
        new AcknowledgementExecutionContextInstrumentation(),
        new MessageHeaderUtilsInstrumentation());
  }

  @Override
  public boolean isIndyReady() {
    return true;
  }
}
