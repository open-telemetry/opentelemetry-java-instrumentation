/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v4_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.RedisCommandSanitizer;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;

final class JedisDbAttributesGetter implements DbClientAttributesGetter<JedisRequest, Void> {

  private final RedisCommandSanitizer sanitizer;

  JedisDbAttributesGetter(boolean statementSanitizerEnabled) {
    this.sanitizer = RedisCommandSanitizer.create(statementSanitizerEnabled);
  }

  @SuppressWarnings("deprecation") // using deprecated DbSystemIncubatingValues
  @Override
  public String getDbSystem(JedisRequest request) {
    return DbIncubatingAttributes.DbSystemIncubatingValues.REDIS;
  }

  @Override
  public String getDbNamespace(JedisRequest request) {
    return null;
  }

  @Override
  public String getDbQueryText(JedisRequest request) {
    return sanitizer.sanitize(request.getOperation(), request.getArgs());
  }

  @Override
  public String getDbOperationName(JedisRequest request) {
    return request.getOperation();
  }

  @Override
  public InetSocketAddress getNetworkPeerInetSocketAddress(
      JedisRequest request, @Nullable Void unused) {
    SocketAddress address = request.getRemoteSocketAddress();
    if (address instanceof InetSocketAddress) {
      return (InetSocketAddress) address;
    }
    return null;
  }
}
