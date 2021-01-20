/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v1_11.sqs;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonMap;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.List;
import java.util.Map;

@AutoService(InstrumentationModule.class)
public class AwsSdkSqsInstrumentationModule extends InstrumentationModule {
  public AwsSdkSqsInstrumentationModule() {
    super("aws-sdk-sqs", "aws-sdk-1.11");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new AwsClientInstrumentation());
  }

  @Override
  public Map<String, String> contextStore() {
    return singletonMap(
        "com.amazonaws.AmazonWebServiceRequest",
        "io.opentelemetry.javaagent.instrumentation.awssdk.v1_11.RequestMeta");
  }
}
