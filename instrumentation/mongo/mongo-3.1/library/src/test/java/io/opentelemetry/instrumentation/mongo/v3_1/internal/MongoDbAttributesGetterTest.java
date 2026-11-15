/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.mongo.v3_1.internal;

import static io.opentelemetry.instrumentation.mongo.v3_1.internal.MongoInstrumenterFactory.DEFAULT_MAX_NORMALIZED_QUERY_LENGTH;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import com.mongodb.MongoException;
import com.mongodb.MongoSocketException;
import com.mongodb.ServerAddress;
import com.mongodb.connection.ClusterId;
import com.mongodb.connection.ConnectionDescription;
import com.mongodb.connection.ServerId;
import com.mongodb.event.CommandStartedEvent;
import com.mongodb.event.CommandSucceededEvent;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.file.Paths;
import java.util.stream.Stream;
import jnr.unixsocket.UnixSocketAddress;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MongoDbAttributesGetterTest {

  @Test
  @DisplayName("should sanitize queries to json")
  void shouldSanitizeQueriesToJson() {
    MongoDbAttributesGetter extractor =
        new MongoDbAttributesGetter(true, DEFAULT_MAX_NORMALIZED_QUERY_LENGTH);

    assertThat(sanitizeQueryAcrossVersions(extractor, new BsonDocument("cmd", new BsonInt32(1))))
        .isEqualTo("{\"cmd\": \"?\"}");

    assertThat(
            sanitizeQueryAcrossVersions(
                extractor,
                new BsonDocument("cmd", new BsonInt32(1))
                    .append("sub", new BsonDocument("a", new BsonInt32(1)))))
        .isEqualTo("{\"cmd\": \"?\", \"sub\": {\"a\": \"?\"}}");

    assertThat(
            sanitizeQueryAcrossVersions(
                extractor,
                new BsonDocument("cmd", new BsonInt32(1))
                    .append("sub", new BsonArray(singletonList(new BsonInt32(1))))))
        .isEqualTo("{\"cmd\": \"?\", \"sub\": [\"?\"]}");
  }

  @Test
  @DisplayName("should only preserve string value if it is the value of the first top-level key")
  void shouldOnlyPreserveStringValueIfItIsTheValueOfTheFirstTopLevelKey() {
    MongoDbAttributesGetter extractor =
        new MongoDbAttributesGetter(true, DEFAULT_MAX_NORMALIZED_QUERY_LENGTH);

    assertThat(
            sanitizeQueryAcrossVersions(
                extractor,
                new BsonDocument("cmd", new BsonString("c"))
                    .append("f", new BsonString("c"))
                    .append("sub", new BsonString("c"))))
        .isEqualTo("{\"cmd\": \"c\", \"f\": \"?\", \"sub\": \"?\"}");
  }

  @Test
  @DisplayName("should truncate simple command")
  void shouldTruncateSimpleCommand() {
    MongoDbAttributesGetter extractor = new MongoDbAttributesGetter(true, 20);

    String normalized =
        sanitizeQueryAcrossVersions(
            extractor,
            new BsonDocument("cmd", new BsonString("c"))
                .append("f1", new BsonString("c1"))
                .append("f2", new BsonString("c2")));

    // This can vary because of different whitespace for different MongoDB versions
    assertThat(normalized).isIn("{\"cmd\": \"c\", \"f1\": \"", "{\"cmd\": \"c\", \"f1\" ");
  }

  @Test
  @DisplayName("should truncate array")
  void shouldTruncateArray() {
    MongoDbAttributesGetter extractor = new MongoDbAttributesGetter(true, 27);

    String normalized =
        sanitizeQueryAcrossVersions(
            extractor,
            new BsonDocument("cmd", new BsonString("c"))
                .append("f1", new BsonArray(asList(new BsonString("c1"), new BsonString("c2"))))
                .append("f2", new BsonString("c3")));

    // This can vary because of different whitespace for different MongoDB versions
    assertThat(normalized)
        .isIn("{\"cmd\": \"c\", \"f1\": [\"?\", \"?", "{\"cmd\": \"c\", \"f1\": [\"?\",");
  }

  @Test
  void shouldNotSplitSurrogatePairWhenTruncating() {
    MongoDbAttributesGetter extractor = new MongoDbAttributesGetter(true, 20);

    String normalized =
        sanitizeQueryAcrossVersions(
            extractor, new BsonDocument("cmd", new BsonString("aaaaaaaaaa😀")));

    assertThat(normalized)
        .hasSizeLessThanOrEqualTo(20)
        .startsWith("{\"cmd\": \"")
        .doesNotContain(String.valueOf((char) 0xd83d), String.valueOf((char) 0xde00));
  }

  @ParameterizedTest
  @MethodSource("errorTypes")
  void getErrorTypeAndResponseStatusKeepDistinctPolicies(
      Throwable error, String expectedErrorType, String expectedStatus) {
    MongoDbAttributesGetter getter =
        new MongoDbAttributesGetter(true, DEFAULT_MAX_NORMALIZED_QUERY_LENGTH);

    assertThat(getter.getErrorType(null, null, error)).isEqualTo(expectedErrorType);
    assertThat(getter.getDbResponseStatusCode(null, null, error)).isEqualTo(expectedStatus);
  }

  @Test
  void getErrorTypeReturnsWriteConcernErrorCode() {
    MongoDbAttributesGetter getter =
        new MongoDbAttributesGetter(true, DEFAULT_MAX_NORMALIZED_QUERY_LENGTH);
    CommandSucceededEvent response =
        new CommandSucceededEvent(
            1,
            new ConnectionDescription(new ServerId(new ClusterId(), new ServerAddress())),
            "insert",
            BsonDocument.parse("{\"ok\": 1, \"writeConcernError\": {\"code\": 64}}"),
            1);

    assertThat(getter.getErrorType(null, response, null)).isEqualTo("64");
    assertThat(getter.getDbResponseStatusCode(null, response, null)).isEqualTo("64");
  }

  @ParameterizedTest
  @MethodSource("writeErrorStatuses")
  void keepsWriteErrorStatusAndErrorTypePoliciesDistinct(
      String document, String expectedStatus, String expectedErrorType) {
    MongoDbAttributesGetter getter =
        new MongoDbAttributesGetter(true, DEFAULT_MAX_NORMALIZED_QUERY_LENGTH);
    CommandSucceededEvent response =
        new CommandSucceededEvent(
            1,
            new ConnectionDescription(new ServerId(new ClusterId(), new ServerAddress())),
            "insert",
            BsonDocument.parse(document),
            1);

    assertThat(getter.getDbResponseStatusCode(null, response, null)).isEqualTo(expectedStatus);
    assertThat(getter.getErrorType(null, response, null)).isEqualTo(expectedErrorType);
  }

  private static Stream<Arguments> writeErrorStatuses() {
    return Stream.of(
        argumentSet("no error code", "{\"ok\": 1}", null, null),
        argumentSet(
            "negative write error code",
            "{\"ok\": 1, \"writeErrors\": [{\"code\": -3}]}",
            "-3",
            null),
        argumentSet(
            "zero write concern error code",
            "{\"ok\": 1, \"writeConcernError\": {\"code\": 0}}",
            "0",
            null),
        argumentSet(
            "negative write error before positive write error",
            "{\"ok\": 1, \"writeErrors\": [{\"code\": -3}, {\"code\": 11000}]}",
            "-3",
            "11000"),
        argumentSet(
            "zero write error before positive write error",
            "{\"ok\": 1, \"writeErrors\": [{\"code\": 0}, {\"code\": 11000}]}",
            "0",
            "11000"),
        argumentSet(
            "nonpositive write error before positive write concern error",
            "{\"ok\": 1, \"writeErrors\": [{\"code\": -3}], \"writeConcernError\": {\"code\": 64}}",
            "-3",
            "64"),
        argumentSet(
            "minimum int64 write error code",
            "{\"ok\": 1, \"writeErrors\": [{\"code\": {\"$numberLong\": \"-2147483648\"}}]}",
            "-2147483648",
            null),
        argumentSet(
            "below minimum int64 write error code",
            "{\"ok\": 1, \"writeErrors\": [{\"code\": {\"$numberLong\": \"-2147483649\"}}]}",
            null,
            null),
        argumentSet(
            "string code is unavailable",
            "{\"ok\": 1, \"writeErrors\": [{\"code\": \"3\"}]}",
            null,
            null),
        argumentSet(
            "out-of-range code is unavailable",
            "{\"ok\": 1, \"writeErrors\": [{\"code\": 2147483648}]}",
            null,
            null));
  }

  @Test
  void networkPeerComesOnlyFromTheAgentResolver() {
    CommandStartedEvent event = commandStartedEvent();
    InetSocketAddress socketAddress =
        new InetSocketAddress(InetAddress.getLoopbackAddress(), 27018);
    MongoNetworkPeer peer = MongoNetworkPeer.fromSocketAddress(socketAddress);

    MongoDbAttributesGetter libraryGetter =
        new MongoDbAttributesGetter(true, DEFAULT_MAX_NORMALIZED_QUERY_LENGTH);
    MongoDbAttributesGetter agentGetter =
        new MongoDbAttributesGetter(true, DEFAULT_MAX_NORMALIZED_QUERY_LENGTH, ignored -> peer);

    assertThat(libraryGetter.getNetworkPeerAddress(event, null)).isNull();
    assertThat(libraryGetter.getNetworkPeerPort(event, null)).isNull();
    assertThat(libraryGetter.getNetworkPeerInetSocketAddress(event, null)).isNull();
    assertThat(agentGetter.getNetworkPeerAddress(event, null))
        .isEqualTo(InetAddress.getLoopbackAddress().getHostAddress());
    assertThat(agentGetter.getNetworkPeerPort(event, null)).isEqualTo(27018);
    assertThat(agentGetter.getNetworkPeerInetSocketAddress(event, null)).isSameAs(socketAddress);
  }

  @Test
  void networkPeerUsesTheNumericIpv6AddressInsteadOfTheHostLabel() throws UnknownHostException {
    byte[] address = new byte[16];
    address[15] = 1;
    InetAddress ipv6Address = InetAddress.getByAddress("[::1]", address);
    MongoNetworkPeer peer =
        MongoNetworkPeer.fromSocketAddress(new InetSocketAddress(ipv6Address, 27018));
    MongoDbAttributesGetter getter =
        new MongoDbAttributesGetter(true, DEFAULT_MAX_NORMALIZED_QUERY_LENGTH, ignored -> peer);

    assertThat(getter.getNetworkPeerAddress(commandStartedEvent(), null))
        .isEqualTo("0:0:0:0:0:0:0:1");
    assertThat(getter.getNetworkPeerPort(commandStartedEvent(), null)).isEqualTo(27018);
  }

  @Test
  @EnabledForJreRange(min = JRE.JAVA_16)
  void unixNetworkPeerHasNoPort() throws ReflectiveOperationException {
    String socketPath = Paths.get("/tmp/mongodb-27017.sock").toString();
    Class<?> unixDomainSocketAddress = Class.forName("java.net.UnixDomainSocketAddress");
    SocketAddress socketAddress =
        (SocketAddress)
            unixDomainSocketAddress.getMethod("of", String.class).invoke(null, socketPath);
    MongoNetworkPeer peer = MongoNetworkPeer.fromSocketAddress(socketAddress);
    MongoDbAttributesGetter getter =
        new MongoDbAttributesGetter(true, DEFAULT_MAX_NORMALIZED_QUERY_LENGTH, ignored -> peer);

    assertThat(getter.getNetworkPeerAddress(commandStartedEvent(), null)).isEqualTo(socketPath);
    assertThat(getter.getNetworkPeerPort(commandStartedEvent(), null)).isNull();
    assertThat(getter.getNetworkPeerInetSocketAddress(commandStartedEvent(), null)).isNull();
  }

  @Test
  void jnrUnixNetworkPeerHasNoPort() {
    String socketPath = Paths.get("/tmp/mongodb-27017.sock").toString();
    MongoNetworkPeer peer = MongoNetworkPeer.fromSocketAddress(new UnixSocketAddress(socketPath));
    MongoDbAttributesGetter getter =
        new MongoDbAttributesGetter(true, DEFAULT_MAX_NORMALIZED_QUERY_LENGTH, ignored -> peer);

    assertThat(getter.getNetworkPeerAddress(commandStartedEvent(), null)).isEqualTo(socketPath);
    assertThat(getter.getNetworkPeerPort(commandStartedEvent(), null)).isNull();
    assertThat(getter.getNetworkPeerInetSocketAddress(commandStartedEvent(), null)).isNull();
  }

  private static Stream<Arguments> errorTypes() {
    return Stream.of(
        argumentSet(
            "server error code", new MongoException(11000, "duplicate key"), "11000", "11000"),
        argumentSet(
            "zero code", new MongoException(0, "boom"), MongoException.class.getName(), "0"),
        argumentSet(
            "missing code sentinel",
            new MongoException(-1, "boom"),
            MongoException.class.getName(),
            "-1"),
        argumentSet(
            "client message sentinel (-3)",
            new MongoException("boom"),
            MongoException.class.getName(),
            "-3"),
        argumentSet(
            "client message-and-cause sentinel (-4)",
            new MongoException("boom", new RuntimeException()),
            MongoException.class.getName(),
            "-4"),
        argumentSet(
            "socket exception sentinel (-2)",
            new MongoSocketException("boom", new ServerAddress()),
            MongoSocketException.class.getName(),
            "-2"),
        argumentSet(
            "non-mongo exception",
            new IllegalStateException("boom"),
            IllegalStateException.class.getName(),
            null),
        argumentSet("no error", null, null, null));
  }

  private static CommandStartedEvent commandStartedEvent() {
    ConnectionDescription connectionDescription =
        new ConnectionDescription(
            new ServerId(new ClusterId(), new ServerAddress("configured.example", 27017)));
    return new CommandStartedEvent(
        1,
        connectionDescription,
        "test",
        "find",
        new BsonDocument("find", new BsonString("collection")));
  }

  private static String sanitizeQueryAcrossVersions(
      MongoDbAttributesGetter extractor, BsonDocument query) {
    return sanitizeAcrossVersions(extractor.sanitizeQuery(query));
  }

  private static String sanitizeAcrossVersions(String json) {
    json = json.replaceAll("\\{ ", "{");
    json = json.replaceAll(" }", "}");
    json = json.replaceAll(" :", ":");
    return json;
  }
}
