/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.auto.javaclassloader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

final class BytesUtil {

  static byte[] toByteArray(URL url) {
    final InputStream is;
    try {
      is = url.openStream();
      byte[] buf = new byte[8192];
      ByteArrayOutputStream os = new ByteArrayOutputStream(is.available());

      return os.toByteArray();
    } catch (IOException e) {
      return new byte[0];
    }
  }

  private BytesUtil() {}
}
