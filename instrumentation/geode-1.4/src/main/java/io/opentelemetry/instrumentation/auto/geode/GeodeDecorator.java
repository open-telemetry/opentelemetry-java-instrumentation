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

package io.opentelemetry.instrumentation.auto.geode;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.instrumentation.api.decorator.DatabaseClientDecorator;
import io.opentelemetry.instrumentation.auto.api.jdbc.DbSystem;
import io.opentelemetry.trace.Tracer;
import org.apache.geode.cache.Region;

public class GeodeDecorator extends DatabaseClientDecorator<Region> {
  public static GeodeDecorator DECORATE = new GeodeDecorator();

  public static final Tracer TRACER = OpenTelemetry.getTracer("io.opentelemetry.auto.geode-1.7");

  @Override
  protected String dbSystem() {
    return DbSystem.GEODE;
  }

  @Override
  protected String dbUser(Region region) {
    return null;
  }

  @Override
  protected String dbName(Region region) {
    return region.getName();
  }
}
