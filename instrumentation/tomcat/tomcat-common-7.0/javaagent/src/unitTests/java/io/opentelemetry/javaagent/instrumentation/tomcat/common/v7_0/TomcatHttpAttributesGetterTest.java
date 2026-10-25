/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.common.v7_0;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.apache.coyote.Request;
import org.apache.tomcat.util.buf.MessageBytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TomcatHttpAttributesGetterTest {
  private final TomcatHttpAttributesGetter getter = new TomcatHttpAttributesGetter();

  @Test
  void missingAndSingleHeaders() {
    Request request = new Request();
    assertThat(getter.getHttpRequestHeader(request, "user-agent")).isEmpty();

    byte[] value = "test-client".getBytes(US_ASCII);
    MessageBytes header = request.getMimeHeaders().addValue("User-Agent");
    header.setBytes(value, 0, value.length);
    List<String> first = getter.getHttpRequestHeader(request, "user-agent");
    assertThat(first).containsExactly("test-client");
    assertThat(header.getType()).isEqualTo(MessageBytes.T_BYTES);

    request.getMimeHeaders().addValue("user-agent").setString("another-client");
    assertThat(first).containsExactly("test-client");
    assertThat(getter.getHttpRequestHeader(request, "USER-AGENT"))
        .containsExactly("test-client", "another-client");
  }

  @Test
  void preservesHeaderOrderAndEmptyValues() {
    Request request = new Request();
    request.getMimeHeaders().addValue("X-Test").setString("");
    request.getMimeHeaders().addValue("Other").setString("ignored");
    request.getMimeHeaders().addValue("x-test").setString("second");
    request.getMimeHeaders().addValue("X-TEST").setString("third");
    assertThat(getter.getHttpRequestHeader(request, "x-test"))
        .containsExactly("", "second", "third");
  }

  @Test
  void http11ProtocolDoesNotChangeMessageBytesType() {
    Request request = new Request();
    byte[] protocol = "HTTP/1.1".getBytes(US_ASCII);
    request.protocol().setBytes(protocol, 0, protocol.length);
    assertThat(getter.getNetworkProtocolName(request, null)).isEqualTo("http");
    assertThat(getter.getNetworkProtocolVersion(request, null)).isEqualTo("1.1");
    assertThat(request.protocol().getType()).isEqualTo(MessageBytes.T_BYTES);
  }

  @ParameterizedTest
  @ValueSource(strings = {"1.0", "2.0", "3", "", "custom"})
  void otherProtocolsKeepTheirVersion(String version) {
    Request request = new Request();
    request.protocol().setString("HTTP/" + version);
    assertThat(getter.getNetworkProtocolName(request, null)).isEqualTo("http");
    assertThat(getter.getNetworkProtocolVersion(request, null)).isEqualTo(version);
  }

  @Test
  void absentAndNonHttpProtocols() {
    Request request = new Request();
    request.protocol().setString("OTHER");
    assertThat(getter.getNetworkProtocolName(request, null)).isNull();
    assertThat(getter.getNetworkProtocolVersion(request, null)).isNull();
    request.protocol().recycle();
    assertThat(getter.getNetworkProtocolName(request, null)).isNull();
    assertThat(getter.getNetworkProtocolVersion(request, null)).isNull();
  }
}
