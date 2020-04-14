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
package io.opentelemetry.auto.instrumentation.api;

// standard tag names (and span kind values) from OpenTracing (see io.opentracing.tag.Tags)
public class Tags {

  public static final String HTTP_URL = "http.url";
  public static final String HTTP_STATUS = "http.status_code";
  public static final String HTTP_METHOD = "http.method";
  public static final String DB_TYPE = "db.type";
  public static final String DB_INSTANCE = "db.instance";
  public static final String DB_USER = "db.user";
  public static final String DB_STATEMENT = "db.statement";
  public static final String DB_URL = "db.url";
}
