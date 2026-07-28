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
                new Class<?>[] {String.class, int.class, Object.class},
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
                "delete",
                new Class<?>[] {String.class, int.class},
                new Object[] {"my-key", 5},
                true))
        .isEqualTo("delete my-key");
  }

  @Test
  void shouldCaptureCasArgumentOfDelete() {
    // delete(String key, long cas) is a distinct overload whose cas value is actually sent
    assertThat(
            SpymemcachedQueryText.create(
                "delete",
                new Class<?>[] {String.class, long.class},
                new Object[] {"my-key", 5L},
                true))
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
    // the transcoder parameter position is known from the method signature, so a null value there
    // must not be mistaken for the stored value and leak it unmasked
    assertThat(
            SpymemcachedQueryText.create(
                "set",
                new Class<?>[] {String.class, int.class, Object.class, Transcoder.class},
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
                new Class<?>[] {String.class, int.class, Object.class},
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

  /**
   * Infers parameter types from the runtime class of each (non-null) argument. This is accurate
   * enough for tests that do not exercise the null-transcoder or primitive-parameter edge cases,
   * which instead call {@link SpymemcachedQueryText#create} directly with explicit types.
   */
  private static String create(String operationName, Object[] args) {
    Class<?>[] parameterTypes = new Class<?>[args.length];
    for (int i = 0; i < args.length; i++) {
      parameterTypes[i] = args[i] == null ? Object.class : args[i].getClass();
    }
    return SpymemcachedQueryText.create(operationName, parameterTypes, args, true);
  }
}
