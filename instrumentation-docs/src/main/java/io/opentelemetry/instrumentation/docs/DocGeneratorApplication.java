/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs;

import io.opentelemetry.instrumentation.docs.utils.FileManager;
import io.opentelemetry.instrumentation.docs.utils.YamlHelper;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.logging.Logger;

public class DocGeneratorApplication {

  private static final Logger logger = Logger.getLogger(DocGeneratorApplication.class.getName());

  public static void main(String[] args) {
    FileManager fileManager = new FileManager("instrumentation/");
    List<InstrumentationEntity> entities = new InstrumentationAnalyzer(fileManager).analyze();

    try (BufferedWriter writer =
        Files.newBufferedWriter(
            Paths.get("docs/instrumentation-list.yaml"), Charset.defaultCharset())) {
      YamlHelper.printInstrumentationList(entities, writer);
    } catch (IOException e) {
      logger.severe("Error writing instrumentation list: " + e.getMessage());
    }
  }

  private DocGeneratorApplication() {}
}
