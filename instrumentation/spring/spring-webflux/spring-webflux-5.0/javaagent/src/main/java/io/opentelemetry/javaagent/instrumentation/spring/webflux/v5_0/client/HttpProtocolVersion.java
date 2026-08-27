/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.v5_0.client;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.lang.reflect.Method;
import javax.annotation.Nullable;
import org.springframework.http.client.reactive.ClientHttpResponse;
import org.springframework.web.reactive.function.client.ClientResponse;

public class HttpProtocolVersion {

  private static final VirtualField<ClientHttpResponse, String>
      CLIENT_HTTP_RESPONSE_PROTOCOL_VERSION =
          VirtualField.find(ClientHttpResponse.class, String.class);
  public static final VirtualField<ClientResponse, String> CLIENT_RESPONSE_PROTOCOL_VERSION =
      VirtualField.find(ClientResponse.class, String.class);

  // the reactor and netty types are resolved reflectively; a compile-time reference to them would
  // add a muzzle reference that disables this instrumentation module in applications whose
  // WebClient does not use reactor netty
  private static final ClassValue<VersionAccessor> versionAccessor =
      new ClassValue<VersionAccessor>() {
        @Nullable
        @Override
        protected VersionAccessor computeValue(Class<?> responseClass) {
          String responseTypeName =
              responseClass.getName().startsWith("reactor.ipc.")
                  ? "reactor.ipc.netty.http.client.HttpClientResponse"
                  : "reactor.netty.http.client.HttpClientResponse";
          try {
            Class<?> responseType =
                Class.forName(responseTypeName, false, responseClass.getClassLoader());
            Method versionMethod = responseType.getMethod("version");
            Class<?> versionType = versionMethod.getReturnType();
            return new VersionAccessor(
                versionMethod,
                versionType.getMethod("majorVersion"),
                versionType.getMethod("minorVersion"));
          } catch (ReflectiveOperationException ignored) {
            return null;
          }
        }
      };

  public static void set(ClientHttpResponse response, Object reactorClientHttpResponse) {
    VersionAccessor accessor = versionAccessor.get(reactorClientHttpResponse.getClass());
    if (accessor != null) {
      try {
        CLIENT_HTTP_RESPONSE_PROTOCOL_VERSION.set(
            response, accessor.getProtocolVersion(reactorClientHttpResponse));
      } catch (ReflectiveOperationException ignored) {
        // ignored
      }
    }
  }

  public static void copy(ClientHttpResponse source, ClientResponse target) {
    String protocolVersion = CLIENT_HTTP_RESPONSE_PROTOCOL_VERSION.get(source);
    if (protocolVersion != null) {
      CLIENT_RESPONSE_PROTOCOL_VERSION.set(target, protocolVersion);
    }
  }

  static String format(int majorVersion, int minorVersion) {
    return majorVersion > 1 && minorVersion == 0
        ? Integer.toString(majorVersion)
        : majorVersion + "." + minorVersion;
  }

  private HttpProtocolVersion() {}

  private static class VersionAccessor {

    private final Method versionMethod;
    private final Method majorVersionMethod;
    private final Method minorVersionMethod;

    VersionAccessor(Method versionMethod, Method majorVersionMethod, Method minorVersionMethod) {
      this.versionMethod = versionMethod;
      this.majorVersionMethod = majorVersionMethod;
      this.minorVersionMethod = minorVersionMethod;
    }

    @Nullable
    String getProtocolVersion(Object reactorClientHttpResponse)
        throws ReflectiveOperationException {
      Object version = versionMethod.invoke(reactorClientHttpResponse);
      if (version == null) {
        return null;
      }
      return format(
          (int) majorVersionMethod.invoke(version), (int) minorVersionMethod.invoke(version));
    }
  }
}
