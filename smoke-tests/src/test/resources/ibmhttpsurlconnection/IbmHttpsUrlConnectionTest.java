/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class IbmHttpsUrlConnectionTest {

  public static void main(String... args) throws Exception {
    HttpURLConnection connection =
        (HttpURLConnection) new URL("https://google.com").openConnection();
    connection.connect();
    InputStream stream = connection.getInputStream();
    stream.close();
  }
}
