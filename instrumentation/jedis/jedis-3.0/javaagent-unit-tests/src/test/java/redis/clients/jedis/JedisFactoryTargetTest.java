/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package redis.clients.jedis;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import org.junit.jupiter.api.Test;

class JedisFactoryTargetTest {

  @Test
  void storesTargetDirectlyWithoutOverwritingUnnamedField() {
    TestJedisFactory factory = new TestJedisFactory();
    Object unrelated = new Object();
    Object target = new Object();
    VirtualField<JedisFactory, Object> unnamed =
        VirtualField.find(JedisFactory.class, Object.class);
    VirtualField<JedisFactory, Object> named =
        VirtualField.find(JedisFactory.class, Object.class, "configuredTarget");
    unnamed.set(factory, unrelated);

    assertThat(JedisFactoryTarget.get(factory)).isNull();
    JedisFactoryTarget.set(factory, target);

    assertThat(JedisFactoryTarget.get(factory)).isSameAs(target);
    assertThat(named.get(factory)).isSameAs(target);
    assertThat(unnamed.get(factory)).isSameAs(unrelated);
    assertThat(JedisFactoryTarget.get(new Object())).isNull();
  }
}
