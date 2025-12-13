/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.v3_0;

import jakarta.json.stream.JsonGenerator;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import javax.annotation.Nullable;
import org.apache.hc.core5.http.ContentType;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.NdJsonpSerializable;
import org.opensearch.client.transport.GenericSerializable;

public class OpenSearchBodyExtractor {

  @Nullable
  public static String extract(JsonpMapper mapper, Object request) {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();

      if (request instanceof NdJsonpSerializable) {
        writeNdJson(mapper, (NdJsonpSerializable) request, baos);
      } else if (request instanceof GenericSerializable) {
        ContentType.parse(((GenericSerializable) request).serialize(baos));
      } else {
        JsonGenerator generator = mapper.jsonProvider().createGenerator(baos);
        mapper.serialize(request, generator);
        generator.close();
      }

      String body = baos.toString(StandardCharsets.UTF_8);
      return body.isEmpty() ? null : body;
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static void writeNdJson(
      JsonpMapper mapper, NdJsonpSerializable value, ByteArrayOutputStream baos) {
    try {
      Iterator<?> values = value._serializables();
      while (values.hasNext()) {
        Object item = values.next();
        if (item instanceof NdJsonpSerializable && item != value) {
          // do not recurse on the item itself
          writeNdJson(mapper, (NdJsonpSerializable) item, baos);
        } else {
          JsonGenerator generator = mapper.jsonProvider().createGenerator(baos);
          mapper.serialize(item, generator);
          generator.close();
          baos.write('\n');
        }
      }
    } catch (RuntimeException e) {
      // Ignore
    }
  }

  private OpenSearchBodyExtractor() {}
}
