/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.util.internal;

import static io.opentelemetry.instrumentation.api.incubator.semconv.util.internal.GlobUtil.toGlobPatternPredicate;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GlobUtilTest {

  @Test
  void matchesName() {
    assertThat(toGlobPatternPredicate("foo").test("foo")).isTrue();
    assertThat(toGlobPatternPredicate("foo").test("Foo")).isTrue();
    assertThat(toGlobPatternPredicate("foo").test("bar")).isFalse();
    assertThat(toGlobPatternPredicate("fo?").test("foo")).isTrue();
    assertThat(toGlobPatternPredicate("fo??").test("fooo")).isTrue();
    assertThat(toGlobPatternPredicate("fo?").test("fob")).isTrue();
    assertThat(toGlobPatternPredicate("fo?").test("fooo")).isFalse();
    assertThat(toGlobPatternPredicate("*").test("foo")).isTrue();
    assertThat(toGlobPatternPredicate("*").test("bar")).isTrue();
    assertThat(toGlobPatternPredicate("*").test("baz")).isTrue();
    assertThat(toGlobPatternPredicate("*").test("foo.bar.baz")).isTrue();
    assertThat(toGlobPatternPredicate("*").test(null)).isTrue();
    assertThat(toGlobPatternPredicate("*").test("")).isTrue();
    assertThat(toGlobPatternPredicate("fo*").test("fo")).isTrue();
    assertThat(toGlobPatternPredicate("fo*").test("foo")).isTrue();
    assertThat(toGlobPatternPredicate("fo*").test("fooo")).isTrue();
    assertThat(toGlobPatternPredicate("fo*").test("foo.bar.baz")).isTrue();
    assertThat(toGlobPatternPredicate("*bar").test("sandbar")).isTrue();
    assertThat(toGlobPatternPredicate("fo*b*").test("foobar")).isTrue();
    assertThat(toGlobPatternPredicate("fo*b*").test("foob")).isTrue();
    assertThat(toGlobPatternPredicate("fo*b*").test("foo bar")).isTrue();
    assertThat(toGlobPatternPredicate("fo? b??").test("foo bar")).isTrue();
    assertThat(toGlobPatternPredicate("fo? b??").test("fooo bar")).isFalse();
    assertThat(toGlobPatternPredicate("fo* ba?").test("foo is not bar")).isTrue();
    assertThat(toGlobPatternPredicate("fo? b*").test("fox beetles for lunch")).isTrue();
    assertThat(toGlobPatternPredicate("f()[]$^.{}|").test("f()[]$^.{}|")).isTrue();
    assertThat(toGlobPatternPredicate("f()[]$^.{}|?").test("f()[]$^.{}|o")).isTrue();
    assertThat(toGlobPatternPredicate("f()[]$^.{}|*").test("f()[]$^.{}|ooo")).isTrue();
  }
}
