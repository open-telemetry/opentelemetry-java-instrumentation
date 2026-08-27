/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.v3_0;

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;

class OpenSearchBodyExtractorTest {

  @Test
  void shouldUseJacksonMapperJsonFactory() {
    JsonFactory jsonFactory =
        JsonFactory.builder().enable(JsonWriteFeature.ESCAPE_NON_ASCII).build();
    JacksonJsonpMapper mapper = new JacksonJsonpMapper(new ObjectMapper(jsonFactory));

    String result =
        OpenSearchBodyExtractor.extractSanitized(mapper, singletonMap("m\u00e9ssage", "secret"));

    assertThat(result).isEqualTo("{\"m\\u00E9ssage\":\"?\"}");
  }
}
