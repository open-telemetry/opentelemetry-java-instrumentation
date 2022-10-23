/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class ElasticTransportRequest {

  public static ElasticTransportRequest create(Object action, Object request) {
    return new AutoValue_ElasticTransportRequest(action, request);
  }

  public abstract Object getAction();

  public abstract Object getRequest();
}
