/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.armeria.v1_3.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import com.linecorp.armeria.common.RequestContext;
import java.net.SocketAddress;
import org.junit.jupiter.api.Test;

class RequestContextAccessTest {

  @Test
  @SuppressWarnings(
      "MockitoDoSetup") // Armeria 1.24+ narrows the return types to InetSocketAddress.
  void preservesGenericSocketAddresses() throws Exception {
    assumeTrue(
        RequestContext.class.getMethod("remoteAddress").getReturnType() == SocketAddress.class);
    SocketAddress local = mock(SocketAddress.class);
    SocketAddress peer = mock(SocketAddress.class);
    RequestContext context = mock(RequestContext.class);
    doReturn(local).when(context).localAddress();
    doReturn(peer).when(context).remoteAddress();

    assertThat(RequestContextAccess.localAddress(context)).isSameAs(local);
    assertThat(RequestContextAccess.remoteAddress(context)).isSameAs(peer);
  }
}
