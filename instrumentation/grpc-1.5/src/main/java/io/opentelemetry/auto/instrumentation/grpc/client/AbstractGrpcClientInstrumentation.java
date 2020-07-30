/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.auto.instrumentation.grpc.client;

import io.opentelemetry.auto.tooling.Instrumenter;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Map;

abstract class AbstractGrpcClientInstrumentation extends Instrumenter.Default {

  public AbstractGrpcClientInstrumentation() {
    super("grpc", "grpc-client");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".GrpcClientDecorator",
      packageName + ".GrpcInjectAdapter",
      packageName + ".TracingClientInterceptor",
      packageName + ".TracingClientInterceptor$TracingClientCall",
      packageName + ".TracingClientInterceptor$TracingClientCallListener",
      "io.opentelemetry.auto.instrumentation.grpc.common.GrpcHelper",
    };
  }

  @Override
  public Map<String, String> contextStore() {
    return Collections.singletonMap(
        "io.grpc.ManagedChannelBuilder", InetSocketAddress.class.getName());
  }
}
