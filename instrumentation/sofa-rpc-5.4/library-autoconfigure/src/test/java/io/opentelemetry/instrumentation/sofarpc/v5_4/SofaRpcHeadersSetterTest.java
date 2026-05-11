/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.sofarpc.v5_4;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.alipay.sofa.rpc.core.request.SofaRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SofaRpcHeadersSetterTest {

  @Mock SofaRequest sofaRequest;

  @Test
  void set() {
    SofaRpcHeadersSetter.INSTANCE.set(SofaRpcRequest.create(sofaRequest), "key", "value");

    verify(sofaRequest).addRequestProp("key", "value");
  }

  @Test
  void setNullCarrier() {
    SofaRpcHeadersSetter.INSTANCE.set(null, "key", "value");

    verifyNoInteractions(sofaRequest);
  }
}