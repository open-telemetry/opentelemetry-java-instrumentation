/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.geode;

import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.stream.Stream;
import org.apache.geode.DataSerializable;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.client.ClientCache;
import org.apache.geode.cache.client.ClientCacheFactory;
import org.apache.geode.cache.client.ClientRegionFactory;
import org.apache.geode.cache.client.ClientRegionShortcut;
import org.apache.geode.cache.query.QueryException;
import org.apache.geode.cache.query.SelectResults;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PutGetTest {
  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  static ClientCache cache = new ClientCacheFactory().create();
  static ClientRegionFactory<Object, Object> regionFactory =
      cache.createClientRegionFactory(ClientRegionShortcut.LOCAL);
  static Region<Object, Object> region = regionFactory.create("test-region");

  private static Stream<Arguments> provideParameters() {
    return Stream.of(
        Arguments.of("Hello", "World"),
        Arguments.of("Humpty", "Dumpty"),
        Arguments.of(Integer.valueOf(1), "One"),
        Arguments.of("One", Integer.valueOf(1)));
  }

  @ParameterizedTest
  @MethodSource("provideParameters")
  void testPutAndGet(Object key, Object value) {
    Object cacheValue =
        testing.runWithSpan(
            "someTrace",
            () -> {
              region.clear();
              region.put(key, value);
              return region.get(key);
            });
    assertEquals(value, cacheValue);
    assertGeodeTrace("get", null);
  }

  @ParameterizedTest
  @MethodSource("provideParameters")
  void testPutAndRemove(Object key, Object value) {
    testing.runWithSpan(
        "someTrace",
        () -> {
          region.clear();
          region.put(key, value);
          region.remove(key);
        });
    assertEquals(0, region.size());
    assertGeodeTrace("remove", null);
  }

  @ParameterizedTest
  @MethodSource("provideParameters")
  void testQuery(Object key, Object value) throws QueryException {
    SelectResults<Object> cacheValue =
        testing.runWithSpan(
            "someTrace",
            () -> {
              region.clear();
              region.put(key, value);
              return region.query("SELECT * FROM /test-region");
            });
    assertEquals(1, cacheValue.size());
    assertGeodeTrace("query", "SELECT * FROM /test-region");
  }

  @ParameterizedTest
  @MethodSource("provideParameters")
  void testExistsValue(Object key, Object value) throws QueryException {
    boolean cacheValue =
        testing.runWithSpan(
            "someTrace",
            () -> {
              region.clear();
              region.put(key, value);
              return region.existsValue("SELECT * FROM /test-region");
            });
    assertTrue(cacheValue);
    assertGeodeTrace("existsValue", "SELECT * FROM /test-region");
  }

  @Test
  void shouldSanitizeGeodeQuery() throws QueryException {
    Card value = new Card("1234432156788765", "10/2020");
    SelectResults<Object> results =
        testing.runWithSpan(
            "someTrace",
            () -> {
              region.clear();
              region.put(1, value);
              return region.query("SELECT * FROM /test-region p WHERE p.expDate = '10/2020'");
            });

    assertEquals(value, results.asList().get(0));
    assertGeodeTrace("query", "SELECT * FROM /test-region p WHERE p.expDate = ?");
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  void assertGeodeTrace(String verb, String query) {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("someTrace").hasKind(SpanKind.INTERNAL),
                span ->
                    span.hasName("clear test-region")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), "geode"),
                            equalTo(maybeStable(DB_NAME), "test-region"),
                            equalTo(maybeStable(DB_OPERATION), "clear")),
                span ->
                    span.hasName("put test-region")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), "geode"),
                            equalTo(maybeStable(DB_NAME), "test-region"),
                            equalTo(maybeStable(DB_OPERATION), "put")),
                span ->
                    span.hasName(verb.concat(" test-region"))
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), "geode"),
                            equalTo(maybeStable(DB_NAME), "test-region"),
                            equalTo(maybeStable(DB_OPERATION), verb),
                            equalTo(maybeStable(DB_STATEMENT), query))));
  }

  static class Card implements DataSerializable {
    String cardNumber;
    String expDate;

    public Card(String cardNumber, String expDate) {
      this.cardNumber = cardNumber;
      this.expDate = expDate;
    }

    public String getCardNumber() {
      return cardNumber;
    }

    public void setCardNumber(String cardNumber) {
      this.cardNumber = cardNumber;
    }

    public String getExpDate() {
      return expDate;
    }

    public void setExpDate(String expDate) {
      this.expDate = expDate;
    }

    @Override
    public void toData(DataOutput dataOutput) throws IOException {
      dataOutput.writeUTF(cardNumber);
      dataOutput.writeUTF(expDate);
    }

    @Override
    public void fromData(DataInput dataInput) throws IOException, ClassNotFoundException {
      cardNumber = dataInput.readUTF();
      expDate = dataInput.readUTF();
    }
  }
}
