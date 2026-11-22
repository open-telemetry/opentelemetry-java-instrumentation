/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package redis.clients.jedis;

public class TestJedisFactory extends JedisFactory {

  public TestJedisFactory() {
    super("localhost", 6379, 2000, 2000, null, 0, null);
  }
}
