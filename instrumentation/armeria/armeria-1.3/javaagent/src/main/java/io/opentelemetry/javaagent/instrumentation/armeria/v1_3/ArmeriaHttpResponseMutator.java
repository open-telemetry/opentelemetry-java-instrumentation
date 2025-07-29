/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.armeria.v1_3;

import com.linecorp.armeria.common.ResponseHeadersBuilder;
import io.opentelemetry.javaagent.bootstrap.http.HttpServerResponseMutator;

enum ArmeriaHttpResponseMutator implements HttpServerResponseMutator<ResponseHeadersBuilder> {
  INSTANCE;

  @Override
  public void appendHeader(ResponseHeadersBuilder response, String name, String value) {
    response.add(name, value);
  }
}
