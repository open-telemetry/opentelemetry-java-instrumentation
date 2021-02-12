/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v1_11;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonMap;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.List;
import java.util.Map;

@AutoService(InstrumentationModule.class)
public class AwsSdkInstrumentationModule extends InstrumentationModule {
  public AwsSdkInstrumentationModule() {
    super("aws-sdk", "aws-sdk-1.11");
  }

  @Override
  public String[] additionalHelperClassNames() {
    return new String[] {"io.opentelemetry.extension.aws.AwsXrayPropagator"};
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new AwsClientInstrumentation(),
        new AwsHttpClientInstrumentation(),
        new RequestExecutorInstrumentation(),
        new RequestInstrumentation());
  }

  @Override
  public Map<String, String> contextStore() {
    return singletonMap(
        "com.amazonaws.AmazonWebServiceRequest",
        "io.opentelemetry.javaagent.instrumentation.awssdk.v1_11.RequestMeta");
  }
}
