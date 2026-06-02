/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.rest.v7_0;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.instrumentation.elasticsearch.rest.common.v5_0.internal.ElasticsearchEndpointDefinition;
import io.opentelemetry.instrumentation.elasticsearch.rest.common.v5_0.internal.ElasticsearchRestRequest;
import io.opentelemetry.javaagent.instrumentation.elasticsearch.rest.common.v5_0.ElasticsearchRestJavaagentInstrumenterFactory;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;

public class ElasticsearchRest7Singletons {

  private static final Instrumenter<ElasticsearchRestRequest, Response> instrumenter =
      ElasticsearchRestJavaagentInstrumenterFactory.create(
          "io.opentelemetry.elasticsearch-rest-7.0");

  public static final VirtualField<Request, ElasticsearchEndpointDefinition> ENDPOINT_DEFINITION =
      VirtualField.find(Request.class, ElasticsearchEndpointDefinition.class);

  public static Instrumenter<ElasticsearchRestRequest, Response> instrumenter() {
    return instrumenter;
  }

  private ElasticsearchRest7Singletons() {}
}
