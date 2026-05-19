/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package redis.clients.jedis;

import javax.annotation.Nullable;

public class DefaultJedisSocketFactoryUtil {

  @Nullable
  public static HostAndPort getHostAndPort(@Nullable JedisSocketFactory socketFactory) {
    if (socketFactory instanceof DefaultJedisSocketFactory) {
      return ((DefaultJedisSocketFactory) socketFactory).getSocketHostAndPort();
    }
    return null;
  }

  private DefaultJedisSocketFactoryUtil() {}
}
