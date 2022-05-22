/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

final class CallDepthThreadLocalMap {

  private static final ClassValue<ThreadLocalDepth> TLS =
      new ClassValue<ThreadLocalDepth>() {
        @Override
        protected ThreadLocalDepth computeValue(Class<?> type) {
          return new ThreadLocalDepth();
        }
      };

  static CallDepth getCallDepth(Class<?> k) {
    return TLS.get(k).get();
  }

  private static final class ThreadLocalDepth extends ThreadLocal<CallDepth> {
    @Override
    protected CallDepth initialValue() {
      return new CallDepth();
    }
  }

  private CallDepthThreadLocalMap() {}
}
