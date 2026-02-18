/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

// Includes work from:
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.opentelemetry.javaagent.instrumentation.thrift.v0_9_1;

import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolDecorator;

/**
 * Note that the 8888th field of record is reserved for transporting trace header. Because Thrift
 * doesn't support to transport metadata.
 */
public abstract class AbstractProtocolWrapper extends TProtocolDecorator {
  public static final String OT_MAGIC_FIELD = "OT_MAGIC_FIELD";
  public static final short OT_MAGIC_FIELD_ID = 8888;

  public AbstractProtocolWrapper(TProtocol protocol) {
    super(protocol);
  }
}
