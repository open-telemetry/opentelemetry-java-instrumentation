/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.nacos.client.v2_0_3.extractors;

import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesGetter;
import io.opentelemetry.javaagent.instrumentation.nacos.client.v2_0_3.NacosClientRequest;
import javax.annotation.Nullable;

public class NacosClientCodeAttributesGetter implements CodeAttributesGetter<NacosClientRequest> {
  @Nullable
  @Override
  public Class<?> getCodeClass(NacosClientRequest nacosClientRequest) {
    return nacosClientRequest.getDeclaringClass();
  }

  @Nullable
  @Override
  public String getMethodName(NacosClientRequest nacosClientRequest) {
    return nacosClientRequest.getMethodName();
  }
}
