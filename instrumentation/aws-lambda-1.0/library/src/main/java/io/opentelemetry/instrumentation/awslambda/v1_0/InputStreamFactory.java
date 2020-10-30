/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.apache.commons.io.IOUtils;

abstract class InputStreamFactory {

  static InputStreamFactory forStream(final InputStream source) throws IOException {

    if (source.markSupported()) {
      return new MarkableStreamFactory(source);
    }
    // fallback
    return new CopiedStreamFactory(source);
  }

  abstract InputStream freshStream() throws IOException;

  private static class MarkableStreamFactory extends InputStreamFactory {

    private final InputStream inputStream;

    private MarkableStreamFactory(InputStream inputStream) {
      this.inputStream = inputStream;
      inputStream.mark(Integer.MAX_VALUE);
    }

    @Override
    InputStream freshStream() throws IOException {

      inputStream.reset();
      inputStream.mark(Integer.MAX_VALUE);
      return inputStream;
    }
  }

  private static class CopiedStreamFactory extends InputStreamFactory {

    private final byte[] data;

    private CopiedStreamFactory(InputStream inputStream) throws IOException {
      data = IOUtils.toByteArray(inputStream);
    }

    @Override
    InputStream freshStream() {
      return new ByteArrayInputStream(data);
    }
  }
}
