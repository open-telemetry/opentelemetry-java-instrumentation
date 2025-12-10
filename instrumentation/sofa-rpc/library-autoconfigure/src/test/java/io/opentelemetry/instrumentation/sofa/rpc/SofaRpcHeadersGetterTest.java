/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.sofa.rpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.alipay.sofa.rpc.core.request.SofaRequest;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SofaRpcHeadersGetterTest {

  @Mock SofaRequest sofaRequest;

  @Test
  void testKeys() {
    Map<String, Object> requestProps = Collections.singletonMap("key", "value");
    when(sofaRequest.getRequestProps()).thenReturn(requestProps);

    SofaRpcRequest request = SofaRpcRequest.create(sofaRequest);

    Iterator<String> iterator = SofaRpcHeadersGetter.INSTANCE.keys(request).iterator();
    assertThat(iterator.hasNext()).isTrue();
    assertThat(iterator.next()).isEqualTo("key");
    assertThat(iterator.hasNext()).isFalse();
  }

  @Test
  void testKeysEmpty() {
    when(sofaRequest.getRequestProps()).thenReturn(Collections.emptyMap());

    SofaRpcRequest request = SofaRpcRequest.create(sofaRequest);

    Iterator<String> iterator = SofaRpcHeadersGetter.INSTANCE.keys(request).iterator();
    assertThat(iterator.hasNext()).isFalse();
  }

  @Test
  void testKeysNull() {
    when(sofaRequest.getRequestProps()).thenReturn(null);

    SofaRpcRequest request = SofaRpcRequest.create(sofaRequest);

    Iterator<String> iterator = SofaRpcHeadersGetter.INSTANCE.keys(request).iterator();
    assertThat(iterator.hasNext()).isFalse();
  }

  @Test
  void testGet() {
    when(sofaRequest.getRequestProp("key")).thenReturn("value");

    SofaRpcRequest request = SofaRpcRequest.create(sofaRequest);

    assertThat(SofaRpcHeadersGetter.INSTANCE.get(request, "key")).isEqualTo("value");
  }

  @Test
  void testGetNonString() {
    when(sofaRequest.getRequestProp("key")).thenReturn(123);

    SofaRpcRequest request = SofaRpcRequest.create(sofaRequest);

    // Should return null for non-String values
    assertThat(SofaRpcHeadersGetter.INSTANCE.get(request, "key")).isNull();
  }

  @Test
  void testGetNull() {
    when(sofaRequest.getRequestProp("key")).thenReturn(null);

    SofaRpcRequest request = SofaRpcRequest.create(sofaRequest);

    assertThat(SofaRpcHeadersGetter.INSTANCE.get(request, "key")).isNull();
  }
}

