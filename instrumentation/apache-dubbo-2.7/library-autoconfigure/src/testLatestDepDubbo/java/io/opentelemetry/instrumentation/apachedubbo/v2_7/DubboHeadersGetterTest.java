/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Iterator;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcInvocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DubboHeadersGetterTest {

  @Mock RpcContext context;

  @Mock RpcInvocation rpcInvocation;

  @Test
  @SuppressWarnings("deprecation") // deprecation for RpcInvocation()
  void testKeys() {
    when(context.getUrl()).thenReturn(new URL("http", "localhost", 1));
    when(context.getRemoteAddress()).thenReturn(new InetSocketAddress(1));
    when(context.getLocalAddress()).thenReturn(new InetSocketAddress(1));

    when(rpcInvocation.getObjectAttachments()).thenReturn(Collections.singletonMap("key", "value"));
    DubboRequest request = DubboRequest.create(rpcInvocation, context);

    Iterator<String> iterator = DubboHeadersGetter.INSTANCE.keys(request).iterator();
    assertThat(iterator.hasNext()).isTrue();
    assertThat(iterator.next()).isEqualTo("key");
    assertThat(iterator.hasNext()).isFalse();
  }
}
