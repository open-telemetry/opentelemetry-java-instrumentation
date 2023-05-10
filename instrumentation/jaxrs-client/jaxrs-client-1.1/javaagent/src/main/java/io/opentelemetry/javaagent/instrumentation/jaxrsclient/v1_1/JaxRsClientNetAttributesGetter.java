/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrsclient.v1_1;

import com.sun.jersey.api.client.ClientRequest;
import com.sun.jersey.api.client.ClientResponse;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import javax.annotation.Nullable;

final class JaxRsClientNetAttributesGetter
    implements NetClientAttributesGetter<ClientRequest, ClientResponse> {

  @Override
  @Nullable
  public String getPeerName(ClientRequest request) {
    return request.getURI().getHost();
  }

  @Override
  public Integer getPeerPort(ClientRequest request) {
    return request.getURI().getPort();
  }
}
