/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrsclient.v2_0;

import io.opentelemetry.context.propagation.TextMapSetter;
import javax.ws.rs.core.MultivaluedMap;
import org.jboss.resteasy.client.jaxrs.internal.ClientInvocation;

enum ClientInvocationHeaderSetter implements TextMapSetter<ClientInvocation> {
  INSTANCE;

  @Override
  public void set(ClientInvocation carrier, String key, String value) {
    // Don't allow duplicates.
    // Using MultivaluedMap instead of CaseInsensitiveMap returned by getHeaders because in versions
    // prior to 3.0.10.Final CaseInsensitiveMap has putSingle(String, Object) which is not present
    // in later versions. Going through MultivaluedMap ensures that we'll be calling
    // putSingle(Object, Object) that is present in all versions.
    MultivaluedMap<String, Object> map = carrier.getHeaders().getHeaders();
    map.putSingle(key, value);
  }
}
