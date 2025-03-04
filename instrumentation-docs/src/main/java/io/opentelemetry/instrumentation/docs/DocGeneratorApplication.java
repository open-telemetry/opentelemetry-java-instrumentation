/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs;

import io.opentelemetry.instrumentation.docs.utils.FileManager;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class DocGeneratorApplication {

  private static final Logger logger = Logger.getLogger(DocGeneratorApplication.class.getName());

  public static void main(String[] args) {
    FileManager fileManager = new FileManager("instrumentation/");
    List<InstrumentationEntity> entities = new InstrumentationAnalyzer(fileManager).analyze();
    printInstrumentationList(entities);
  }

  private static void printInstrumentationList(List<InstrumentationEntity> list) {
    Map<String, List<InstrumentationEntity>> groupedByGroup =
        list.stream()
            .collect(
                Collectors.groupingBy(
                    InstrumentationEntity::getGroup, TreeMap::new, Collectors.toList()));

    try (BufferedWriter writer =
        Files.newBufferedWriter(
            Paths.get("docs/instrumentation-list.yaml"), Charset.defaultCharset())) {
      groupedByGroup.forEach(
          (group, entities) -> {
            try {
              String groupHeader = group + ":\n  instrumentations:\n";
              System.out.print(groupHeader);
              writer.write(groupHeader);

              for (InstrumentationEntity entity : entities) {
                String entityDetails =
                    String.format(
                        "    - name: %s\n      srcPath: %s\n      types:\n",
                        entity.getInstrumentationName(), entity.getSrcPath());
                System.out.print(entityDetails);
                writer.write(entityDetails);

                for (InstrumentationType type : entity.getTypes()) {
                  String typeDetail = "        - " + type + "\n";
                  System.out.print(typeDetail);
                  writer.write(typeDetail);
                }

                if (entity.getTargetVersions() == null || entity.getTargetVersions().isEmpty()) {
                  String targetVersions = "      target_versions: []\n";
                  System.out.print(targetVersions);
                  writer.write(targetVersions);
                } else {
                  String targetVersions = "      target_versions:\n";
                  System.out.print(targetVersions);
                  writer.write(targetVersions);
                  for (String version : entity.getTargetVersions()) {
                    String versionDetail = "        - " + version + "\n";
                    System.out.print(versionDetail);
                    writer.write(versionDetail);
                  }
                }
              }
            } catch (IOException e) {
              logger.severe("Error writing instrumentation list: " + e.getMessage());
            }
          });
    } catch (IOException e) {
      logger.severe("Error writing instrumentation list: " + e.getMessage());
    }
  }

  private DocGeneratorApplication() {}
}
