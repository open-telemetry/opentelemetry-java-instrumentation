/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

// Includes work from:
/*
 * Copyright 2017-2021 The OpenTracing Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.opentelemetry.instrumentation.jdbc.parser;

import java.util.regex.Pattern;

/**
 * Parser for AS400
 *
 * @author oburgosm
 * @since 0.2.12
 */
public class AS400URLParser extends AbstractMatcherURLParser {

  private static final Pattern AS400_URL_PATTERN = Pattern
      .compile(
          "jdbc:as400:\\/\\/(?<host>[^\\/;]+)(\\/(?<instance>[^;\\/]*))?\\/?(;(?<options>.*))?");

  private static final String AS400_TYPE = "as400";

  public AS400URLParser() {
    super(AS400_URL_PATTERN, AS400_TYPE);
  }

}
