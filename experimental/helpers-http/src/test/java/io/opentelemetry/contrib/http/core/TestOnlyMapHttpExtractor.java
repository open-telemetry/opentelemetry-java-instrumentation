/*
 * Copyright 2020, OpenTelemetry Authors
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

package io.opentelemetry.contrib.http.core;

import java.util.Map;
import javax.annotation.Nullable;

/** Only used for testing to pull values from a map constructed in a test case. */
class TestOnlyMapHttpExtractor implements HttpExtractor<Map<String, String>, Map<String, String>> {

  public static final String METHOD = "METHOD";
  public static final String URL = "URL";
  public static final String PATH = "PATH";
  public static final String ROUTE = "ROUTE";
  public static final String USERAGENT = "USERAGENT";
  public static final String FLAVOR = "ROUFLAVORTE";
  public static final String CLIENTIP = "CLIENTIP";
  public static final String STATUS = "STATUS";

  @Override
  public String getMethod(Map<String, String> request) {
    return request.get(METHOD);
  }

  @Override
  public String getUrl(Map<String, String> request) {
    return request.get(URL);
  }

  @Override
  public String getRoute(Map<String, String> request) {
    return request.get(ROUTE);
  }

  @Override
  public String getUserAgent(Map<String, String> request) {
    return request.get(USERAGENT);
  }

  @Override
  public String getHttpFlavor(Map<String, String> request) {
    return request.get(FLAVOR);
  }

  @Override
  public String getClientIp(Map<String, String> request) {
    return request.get(CLIENTIP);
  }

  @Override
  public int getStatusCode(@Nullable Map<String, String> response) {
    if (response == null) {
      return 0;
    }
    String status = response.get(STATUS);
    if (status == null) {
      return 0;
    } else {
      return Integer.parseInt(status);
    }
  }
}
