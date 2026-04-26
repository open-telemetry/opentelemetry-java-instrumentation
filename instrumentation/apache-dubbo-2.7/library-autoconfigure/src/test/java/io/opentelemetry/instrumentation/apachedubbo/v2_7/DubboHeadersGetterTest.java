/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import java.util.Map;
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
  void testKeys() throws ReflectiveOperationException {
    when(context.getUrl()).thenReturn(new URL("http", "localhost", 1));
    when(context.getRemoteAddress()).thenReturn(new InetSocketAddress(1));
    when(context.getLocalAddress()).thenReturn(new InetSocketAddress(1));

    // for latest dep tests call getObjectAttachments, otherwise call getAttachments
    if (testLatestDeps()) {
      when(getObjectAttachments()).thenReturn(singletonMap("key", "value"));
    } else {
      when(rpcInvocation.getAttachments()).thenReturn(singletonMap("key", "value"));
    }
    DubboRequest request = DubboRequest.create(rpcInvocation, context);

    assertThat(new DubboHeadersGetter().keys(request)).containsExactly("key");
  }

  @SuppressWarnings("unchecked")
  private Map<Object, Object> getObjectAttachments() throws ReflectiveOperationException {
    return (Map<Object, Object>)
        RpcInvocation.class.getMethod("getObjectAttachments").invoke(rpcInvocation);
  }
}
