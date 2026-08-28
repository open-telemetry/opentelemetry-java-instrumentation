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
import jakarta.json.spi.JsonProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opensearch.client.json.JsonpSerializable;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.json.jsonb.JsonbJsonpMapper;

class OpenSearchBodyExtractorTest {

  @BeforeAll
  static void loadInstrumentationHelpers() throws ClassNotFoundException {
    Class.forName("org.opensearch.client.transport.rest_client.RestClientTransport");
  }

  @Test
  void shouldUseJacksonMapperJsonFactory() {
    JsonFactory jsonFactory =
        JsonFactory.builder().enable(JsonWriteFeature.ESCAPE_NON_ASCII).build();
    JacksonJsonpMapper mapper = new JacksonJsonpMapper(new ObjectMapper(jsonFactory));

    String result =
        OpenSearchBodyExtractor.extract(mapper, singletonMap("m\u00e9ssage", "secret"), true);

    assertThat(result).isEqualTo("{\"m\\u00E9ssage\":\"?\"}");
  }

  @Test
  void shouldUseGenericSerializationForNonJacksonGenerator() {
    JsonProvider jsonProvider = new JsonbJsonpMapper().jsonProvider();
    JacksonJsonpMapper mapper =
        new JacksonJsonpMapper() {
          @Override
          public JsonProvider jsonProvider() {
            return jsonProvider;
          }
        };
    JsonpSerializable value =
        (generator, unused) -> generator.writeStartObject().write("message", "secret").writeEnd();

    String result = OpenSearchBodyExtractor.extract(mapper, value, true);

    assertThat(result).isEqualTo("{\"message\":\"?\"}");
  }

  @Test
  void shouldSuppressExtractionErrors() {
    JacksonJsonpMapper mapper =
        new JacksonJsonpMapper() {
          @Override
          public JsonProvider jsonProvider() {
            throw new NoClassDefFoundError("missing optional dependency");
          }
        };

    String result =
        OpenSearchBodyExtractor.extract(mapper, singletonMap("message", "secret"), true);

    assertThat(result).isNull();
  }
}
