/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.api;

final class CallDepthThreadLocalMap {

  private static final ClassValue<CallDepth> TLS =
      new ClassValue<CallDepth>() {
        @Override
        protected CallDepth computeValue(Class<?> type) {
          return new CallDepth();
        }
      };

  static CallDepth getCallDepth(Class<?> k) {
    return TLS.get(k);
  }

  private CallDepthThreadLocalMap() {}
}
