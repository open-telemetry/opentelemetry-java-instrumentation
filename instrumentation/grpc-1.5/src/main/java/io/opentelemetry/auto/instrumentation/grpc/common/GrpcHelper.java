/*
 * Copyright 2020, OpenTelemetry Authors
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
package io.opentelemetry.auto.instrumentation.grpc.common;

import io.grpc.ManagedChannelBuilder;
import io.opentelemetry.auto.bootstrap.WeakMap;
import io.opentelemetry.auto.instrumentation.api.MoreTags;
import io.opentelemetry.trace.Span;
import java.util.regex.Pattern;

public class GrpcHelper {
  private static final Pattern IPV4 =
      Pattern.compile(
          "^((0|1\\d?\\d?|2[0-4]?\\d?|25[0-5]?|[3-9]\\d?)\\.){3}(0|1\\d?\\d?|2[0-4]?\\d?|25[0-5]?|[3-9]\\d?)$");

  private static final Pattern IPV6_STD =
      Pattern.compile("^(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$");

  private static final Pattern IPV6_COMPRESSED =
      Pattern.compile(
          "^((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)::((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)$");

  public static class AddressAndPort {
    public final String address;

    public final Integer port;

    public AddressAndPort(final String address, final Integer port) {
      this.address = address;
      this.port = port;
    }

    public String getAddress() {
      return address;
    }

    public int getPort() {
      return port;
    }
  }

  private static final WeakMap<ManagedChannelBuilder, AddressAndPort> builderToAddress =
      WeakMap.Provider.newWeakMap();

  public static void addServiceName(final Span span, final String methodName) {
    String serviceName =
        "(unknown)"; // Spec says it's mandatory, so populate even if we couldn't determine it.
    final int slash = methodName.indexOf('/');
    if (slash != -1) {
      final String fullServiceName = methodName.substring(0, slash);
      final int dot = fullServiceName.lastIndexOf('.');
      if (dot != -1) {
        serviceName = fullServiceName.substring(dot + 1);
      }
    }
    span.setAttribute(MoreTags.RPC_SERVICE, serviceName);
  }

  public static void registerAddressForBuilder(
      final io.grpc.ManagedChannelBuilder builder, final String address, final int port) {
    builderToAddress.put(builder, new AddressAndPort(address, port));
  }

  public static AddressAndPort getAddressForBuilder(final ManagedChannelBuilder builder) {
    return builderToAddress.get(builder);
  }

  public static boolean isNumericAddress(final String addr) {
    return IPV4.matcher(addr).matches()
        || IPV6_STD.matcher(addr).matches()
        || IPV6_COMPRESSED.matcher(addr).matches();
  }
}
