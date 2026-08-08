/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached.v2_12;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Iterator;
import net.spy.memcached.transcoders.SerializingTranscoder;
import net.spy.memcached.transcoders.Transcoder;
import org.junit.jupiter.api.Test;

class SpymemcachedQueryTextTest {

  @Test
  void shouldCaptureKey() {
    assertThat(create("get", new Object[] {"my-key"})).isEqualTo("get my-key");
  }

  @Test
  void shouldCaptureKeyAndExpiration() {
    assertThat(create("touch", new Object[] {"my-key", 3600})).isEqualTo("touch my-key 3600");
  }

  @Test
  void shouldMaskStoredValue() {
    assertThat(create("set", new Object[] {"my-key", 3600, "my-value"}))
        .isEqualTo("set my-key 3600 ?");
    assertThat(create("add", new Object[] {"my-key", 3600, "my-value"}))
        .isEqualTo("add my-key 3600 ?");
    assertThat(create("replace", new Object[] {"my-key", 3600, "my-value"}))
        .isEqualTo("replace my-key 3600 ?");
    assertThat(create("cas", new Object[] {"my-key", 123L, "my-value"}))
        .isEqualTo("cas my-key 123 ?");
  }

  @Test
  void shouldPutKeyFirstForAppendAndPrependWithCas() {
    assertThat(create("append", new Object[] {8241947103L, "my-key", "my-value"}))
        .isEqualTo("append my-key 8241947103 ?");
    assertThat(create("prepend", new Object[] {8241947103L, "my-key", "my-value"}))
        .isEqualTo("prepend my-key 8241947103 ?");
  }

  @Test
  void shouldMaskStoredValueThatIsNotAString() {
    assertThat(create("set", new Object[] {"my-key", 3600, 42})).isEqualTo("set my-key 3600 ?");
    assertThat(create("set", new Object[] {"my-key", 3600, asList("a", "b")}))
        .isEqualTo("set my-key 3600 ?");
  }

  @Test
  void shouldNotMaskValueWhenSanitizationIsDisabled() {
    assertThat(
            SpymemcachedQueryText.create(
                "set",
                "(Ljava/lang/String;ILjava/lang/Object;)V",
                new Object[] {"my-key", 3600, "my-value"},
                false))
        .isEqualTo("set my-key 3600 my-value");
  }

  @Test
  void shouldNotMaskArgumentsOfOperationsWithoutValue() {
    assertThat(create("incr", new Object[] {"my-key", 1})).isEqualTo("incr my-key 1");
    assertThat(create("decr", new Object[] {"my-key", 1, 0L})).isEqualTo("decr my-key 1 0");
    assertThat(create("delete", new Object[] {"my-key"})).isEqualTo("delete my-key");
  }

  @Test
  void shouldIgnoreIgnoredHoldArgumentOfDeprecatedDelete() {
    // delete(String key, int hold) is deprecated and delegates to delete(key), silently dropping
    // the hold argument, so it must not appear in the query text
    assertThat(
            SpymemcachedQueryText.create(
                "delete", "(Ljava/lang/String;I)V", new Object[] {"my-key", 5}, true))
        .isEqualTo("delete my-key");
  }

  @Test
  void shouldCaptureCasArgumentOfDelete() {
    // delete(String key, long cas) is a distinct overload whose cas value is actually sent
    assertThat(
            SpymemcachedQueryText.create(
                "delete", "(Ljava/lang/String;J)V", new Object[] {"my-key", 5L}, true))
        .isEqualTo("delete my-key 5");
  }

  @Test
  void shouldCaptureBulkKeysFromCollection() {
    assertThat(create("getBulk", new Object[] {asList("key1", "key2")}))
        .isEqualTo("getBulk key1 key2");
  }

  @Test
  void shouldCaptureBulkKeysFromArray() {
    assertThat(create("getBulk", new Object[] {new String[] {"key1", "key2"}}))
        .isEqualTo("getBulk key1 key2");
  }

  @Test
  void shouldIgnoreTranscoder() {
    assertThat(create("get", new Object[] {"my-key", new SerializingTranscoder()}))
        .isEqualTo("get my-key");
    assertThat(
            create("set", new Object[] {"my-key", 3600, "my-value", new SerializingTranscoder()}))
        .isEqualTo("set my-key 3600 ?");
  }

  @Test
  void shouldIgnoreNullTranscoder() {
    // a null transcoder is a legitimate call, e.g. set("key", 3600, "value", (Transcoder) null);
    // the transcoder parameter position is known from the method descriptor, so a null value there
    // must not be mistaken for the stored value and leak it unmasked
    assertThat(
            SpymemcachedQueryText.create(
                "set",
                "(Ljava/lang/String;ILjava/lang/Object;Lnet/spy/memcached/transcoders/Transcoder;)V",
                new Object[] {"my-key", 3600, "my-value", null},
                true))
        .isEqualTo("set my-key 3600 ?");
  }

  @Test
  void shouldMaskStoredValueThatImplementsTranscoder() {
    // a stored value that happens to implement Transcoder is still the value, not a transcoder
    // parameter, since the instrumented overload here has no explicit Transcoder parameter
    Transcoder<?> value = new SerializingTranscoder();

    assertThat(
            SpymemcachedQueryText.create(
                "set",
                "(Ljava/lang/String;ILjava/lang/Object;)V",
                new Object[] {"my-key", 3600, value},
                true))
        .isEqualTo("set my-key 3600 ?");
  }

  @Test
  void shouldNotConsumeIteratorOfBulkKeys() {
    Iterator<String> keys = asList("key1", "key2").iterator();

    assertThat(create("getBulk", new Object[] {keys})).isEqualTo("getBulk");
    assertThat(keys.hasNext()).isTrue();
  }

  @Test
  void shouldMaskStoredValueThatImplementsIterator() {
    Iterator<String> value = asList("a", "b").iterator();

    assertThat(create("set", new Object[] {"my-key", 3600, value})).isEqualTo("set my-key 3600 ?");
  }

  @Test
  void shouldTruncateLongQueryText() {
    StringBuilder key = new StringBuilder();
    for (int i = 0; i < SpymemcachedQueryText.LIMIT; i++) {
      key.append('a');
    }

    assertThat(create("get", new Object[] {key.toString()}))
        .hasSize(SpymemcachedQueryText.LIMIT)
        .startsWith("get aaa");
  }

  private static String create(String operationName, Object[] args) {
    StringBuilder descriptor = new StringBuilder("(");
    for (Object arg : args) {
      if (arg == null) {
        descriptor.append("Ljava/lang/Object;");
      } else if (arg instanceof Integer) {
        descriptor.append("I");
      } else if (arg instanceof Long) {
        descriptor.append("J");
      } else if (arg instanceof Boolean) {
        descriptor.append("Z");
      } else if (arg instanceof Byte) {
        descriptor.append("B");
      } else if (arg instanceof Character) {
        descriptor.append("C");
      } else if (arg instanceof Short) {
        descriptor.append("S");
      } else if (arg instanceof Float) {
        descriptor.append("F");
      } else if (arg instanceof Double) {
        descriptor.append("D");
      } else {
        descriptor.append("L").append(arg.getClass().getName().replace('.', '/')).append(";");
      }
    }
    descriptor.append(")V");
    return SpymemcachedQueryText.create(operationName, descriptor.toString(), args, true);
  }
}
