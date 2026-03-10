/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.apiclient;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.instrumentation.elasticsearch.rest.common.v5_0.internal.ElasticsearchEndpointDefinition;
import org.elasticsearch.client.Request;

public class ElasticsearchApiClientSingletons {

  public static final VirtualField<Request, ElasticsearchEndpointDefinition> ENDPOINT_DEFINITION =
      VirtualField.find(Request.class, ElasticsearchEndpointDefinition.class);

  private ElasticsearchApiClientSingletons() {}
}
