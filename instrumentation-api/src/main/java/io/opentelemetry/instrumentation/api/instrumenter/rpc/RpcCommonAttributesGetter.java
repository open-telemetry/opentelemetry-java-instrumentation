/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.rpc;

import javax.annotation.Nullable;

/** An interface for getting RPC attributes common to clients and servers. */
public interface RpcCommonAttributesGetter<REQUEST> {

  @Nullable
  String system(REQUEST request);

  @Nullable
  String service(REQUEST request);

  @Nullable
  String method(REQUEST request);
}
