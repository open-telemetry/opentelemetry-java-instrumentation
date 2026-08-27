/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.v5_0.client;

import io.netty.handler.codec.http.HttpVersion;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.lang.reflect.Method;
import javax.annotation.Nullable;
import org.springframework.http.client.reactive.ClientHttpResponse;

public class HttpProtocolVersion {

  private static final VirtualField<ClientHttpResponse, String> PROTOCOL_VERSION =
      VirtualField.find(ClientHttpResponse.class, String.class);

  private static final ClassValue<Method> versionMethod =
      new ClassValue<Method>() {
        @Nullable
        @Override
        protected Method computeValue(Class<?> responseClass) {
          String responseTypeName =
              responseClass.getName().startsWith("reactor.ipc.")
                  ? "reactor.ipc.netty.http.client.HttpClientResponse"
                  : "reactor.netty.http.client.HttpClientResponse";
          try {
            Class<?> responseType =
                Class.forName(responseTypeName, false, responseClass.getClassLoader());
            return responseType.getMethod("version");
          } catch (ReflectiveOperationException ignored) {
            return null;
          }
        }
      };

  public static void set(ClientHttpResponse response, Object reactorClientHttpResponse) {
    Method method = versionMethod.get(reactorClientHttpResponse.getClass());
    if (method != null) {
      try {
        set(response, (HttpVersion) method.invoke(reactorClientHttpResponse));
      } catch (ReflectiveOperationException ignored) {
        // ignored
      }
    }
  }

  public static void set(ClientHttpResponse response, HttpVersion version) {
    PROTOCOL_VERSION.set(response, format(version));
  }

  static String format(HttpVersion version) {
    return version.minorVersion() == 0
        ? Integer.toString(version.majorVersion())
        : version.majorVersion() + "." + version.minorVersion();
  }

  private HttpProtocolVersion() {}
}
