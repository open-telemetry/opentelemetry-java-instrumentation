/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.net.URL;
import java.net.URLConnection;
import org.junit.jupiter.api.Test;

public class ByteArrayUrlTest {

  @Test
  public void testUrlCreation() throws Exception {
    byte[] content = new byte[] {1, 2, 3, 4};

    URL url = ByteArrayUrl.create("my.data$foo", content);

    assertThat(url).hasPath("my.data%24foo").hasProtocol("x-otel-binary");

    URLConnection connection = url.openConnection();
    assertThat(connection.getContentLengthLong()).isEqualTo(4);
    assertThat(connection.getContentLength()).isEqualTo(4);

    assertThat(connection.getInputStream()).hasBinaryContent(content);
  }
}
