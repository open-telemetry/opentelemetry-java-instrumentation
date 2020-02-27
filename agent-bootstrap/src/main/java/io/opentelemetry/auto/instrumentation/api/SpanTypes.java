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

public class SpanTypes {
  public static final String HTTP_CLIENT = "http";
  public static final String HTTP_SERVER = "web";
  @Deprecated public static final String WEB_SERVLET = HTTP_SERVER;
  public static final String RPC = "rpc";
  public static final String CACHE = "cache";

  public static final String SQL = "sql";
  public static final String MONGO = "mongodb";
  public static final String CASSANDRA = "cassandra";
  public static final String COUCHBASE = "db"; // Using generic for now.
  public static final String REDIS = "redis";
  public static final String MEMCACHED = "memcached";
  public static final String ELASTICSEARCH = "elasticsearch";
  public static final String HIBERNATE = "hibernate";

  public static final String MESSAGE_CLIENT = "queue";
  public static final String MESSAGE_CONSUMER = "queue";
  public static final String MESSAGE_PRODUCER = "queue";
}
