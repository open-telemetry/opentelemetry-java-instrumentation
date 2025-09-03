/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import io.grpc.ServerCall;
import io.opentelemetry.instrumentation.api.util.VirtualField;

/**
 * In case a {@link ServerCall} implementation does not implement {@link ServerCall#getAuthority()}
 * like armeria, this utility class should be used to provide the authority instead
 */
public class GrpcAuthorityStorage {

  private static final VirtualField<ServerCall<?, ?>, String> AUTHORITY_FIELD =
      VirtualField.find(ServerCall.class, String.class);

  private GrpcAuthorityStorage() {}

  public static void setAuthority(ServerCall<?, ?> call, String authority) {
    AUTHORITY_FIELD.set(call, authority);
  }

  static String getAuthority(ServerCall<?, ?> call) {
    return AUTHORITY_FIELD.get(call);
  }
}
