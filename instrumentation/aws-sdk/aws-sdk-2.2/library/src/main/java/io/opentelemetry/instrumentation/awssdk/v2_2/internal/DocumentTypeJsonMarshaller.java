/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

// Includes work from:
/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import java.util.List;
import java.util.Map;
import software.amazon.awssdk.core.SdkNumber;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.core.document.VoidDocumentVisitor;
import software.amazon.awssdk.protocols.json.StructuredJsonGenerator;

// Copied as-is from
// https://github.com/aws/aws-sdk-java-v2/blob/7b946c1d03cbfab1252cca0b5845b18af0b2dc43/core/protocols/aws-json-protocol/src/main/java/software/amazon/awssdk/protocols/json/internal/marshall/DocumentTypeJsonMarshaller.java
final class DocumentTypeJsonMarshaller implements VoidDocumentVisitor {

  private final StructuredJsonGenerator jsonGenerator;

  public DocumentTypeJsonMarshaller(StructuredJsonGenerator jsonGenerator) {
    this.jsonGenerator = jsonGenerator;
  }

  @Override
  public void visitNull() {
    jsonGenerator.writeNull();
  }

  @Override
  public void visitBoolean(Boolean document) {
    jsonGenerator.writeValue(document);
  }

  @Override
  public void visitString(String document) {
    jsonGenerator.writeValue(document);
  }

  @Override
  public void visitNumber(SdkNumber document) {
    jsonGenerator.writeNumber(document.stringValue());
  }

  @Override
  public void visitMap(Map<String, Document> documentMap) {
    jsonGenerator.writeStartObject();
    documentMap
        .entrySet()
        .forEach(
            entry -> {
              jsonGenerator.writeFieldName(entry.getKey());
              entry.getValue().accept(this);
            });
    jsonGenerator.writeEndObject();
  }

  @Override
  public void visitList(List<Document> documentList) {
    jsonGenerator.writeStartArray();
    documentList.stream().forEach(document -> document.accept(this));
    jsonGenerator.writeEndArray();
  }
}
