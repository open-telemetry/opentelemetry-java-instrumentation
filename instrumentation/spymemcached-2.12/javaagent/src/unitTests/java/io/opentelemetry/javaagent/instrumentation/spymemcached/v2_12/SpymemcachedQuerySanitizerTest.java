/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached.v2_12;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.nCopies;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Iterator;
import java.util.stream.Stream;
import net.spy.memcached.transcoders.Transcoder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SpymemcachedQuerySanitizerTest {
  private static final Object VALUE =
      new Object() {
        @Override
        public String toString() {
          throw new AssertionError("Values must not be converted to text");
        }
      };
  private static final Transcoder<?> transcoder = mock(Transcoder.class);

  @ParameterizedTest
  @MethodSource("commands")
  void sanitizesCommand(String operation, Object[] args, String expected) {
    assertThat(SpymemcachedQuerySanitizer.sanitize(operation, "", args)).isEqualTo(expected);
    verifyNoInteractions(transcoder);
  }

  private static Stream<Arguments> commands() {
    return Stream.of(
        Arguments.of("get", new Object[] {"key"}, "get key"),
        Arguments.of("get", new Object[] {"key", transcoder}, "get key"),
        Arguments.of("gets", new Object[] {"key", transcoder}, "gets key"),
        Arguments.of("getAndTouch", new Object[] {"key", 3600, transcoder}, "getAndTouch key 3600"),
        Arguments.of("touch", new Object[] {"key", 3600, transcoder}, "touch key 3600"),
        Arguments.of("delete", new Object[] {"key"}, "delete key"),
        Arguments.of("delete", new Object[] {"key", 123L}, "delete key 123"),
        Arguments.of("set", new Object[] {"key", 3600, VALUE}, "set key 3600 ?"),
        Arguments.of("set", new Object[] {"key", 3600, 1234, transcoder}, "set key 3600 ?"),
        Arguments.of("add", new Object[] {"key", 3600, new byte[] {1, 2}}, "add key 3600 ?"),
        Arguments.of(
            "replace", new Object[] {"key", 3600, "private", transcoder}, "replace key 3600 ?"),
        Arguments.of("append", new Object[] {"key", VALUE}, "append key ?"),
        Arguments.of("append", new Object[] {123L, "key", VALUE, transcoder}, "append 123 key ?"),
        Arguments.of("prepend", new Object[] {"key", "private", transcoder}, "prepend key ?"),
        Arguments.of("prepend", new Object[] {123L, "key", VALUE}, "prepend 123 key ?"),
        Arguments.of("incr", new Object[] {"key", 5}, "incr key 5"),
        Arguments.of("decr", new Object[] {"key", 5L}, "decr key 5"),
        Arguments.of("incr", new Object[] {"key", 5L, 1234L}, "incr key 5 ?"),
        Arguments.of("decr", new Object[] {"key", 5, 1234L, 3600}, "decr key 5 ? 3600"),
        Arguments.of("getBulk", new Object[] {asList("key1", "key2")}, "getBulk key1 key2"),
        Arguments.of(
            "getBulk", new Object[] {asList("key1", "key2"), transcoder}, "getBulk key1 key2"),
        Arguments.of("getBulk", new Object[] {new String[] {"key1", "key2"}}, "getBulk key1 key2"),
        Arguments.of(
            "getBulk",
            new Object[] {transcoder, new String[] {"key1", "key2"}},
            "getBulk key1 key2"),
        Arguments.of("getBulk", new Object[] {emptyList()}, "getBulk"),
        Arguments.of("unknown", new Object[] {"private", VALUE}, "unknown"));
  }

  @Test
  void casUsesDeclaredExpirationParameterNotNumericValue() {
    assertThat(
            SpymemcachedQuerySanitizer.sanitize(
                "cas",
                "(Ljava/lang/String;JLjava/lang/Object;)Lnet/spy/memcached/internal/OperationFuture;",
                new Object[] {"key", 123L, 456}))
        .isEqualTo("cas key 123 ?");
    assertThat(
            SpymemcachedQuerySanitizer.sanitize(
                "cas",
                "(Ljava/lang/String;JILjava/lang/Object;)Lnet/spy/memcached/internal/OperationFuture;",
                new Object[] {"key", 123L, 3600, VALUE}))
        .isEqualTo("cas key 123 3600 ?");
  }

  @Test
  void doesNotConsumeKeyOrTranscoderIterators() {
    Iterator<?> keys = mock(Iterator.class);
    Iterator<?> transcoders = mock(Iterator.class);

    assertThat(SpymemcachedQuerySanitizer.sanitize("getBulk", "", new Object[] {keys, transcoders}))
        .isEqualTo("getBulk");
    verifyNoInteractions(keys, transcoders);
  }

  @Test
  void omitsCollectionKeysWhenTranscodersAreAnIterator() {
    Iterator<?> transcoders = singletonList(transcoder).iterator();

    assertThat(
            SpymemcachedQuerySanitizer.sanitize(
                "getBulk",
                "(Ljava/util/Collection;Ljava/util/Iterator;)Lnet/spy/memcached/internal/BulkFuture;",
                new Object[] {asList("key1", "key2"), transcoders}))
        .isEqualTo("getBulk");
    assertThat(transcoders.next()).isSameAs(transcoder);
  }

  @Test
  void limitsBulkQueryLength() {
    assertThat(
            SpymemcachedQuerySanitizer.sanitize(
                "getBulk", "", new Object[] {nCopies(20000, "key")}))
        .hasSize(32 * 1024)
        .startsWith("getBulk key key");
  }
}
