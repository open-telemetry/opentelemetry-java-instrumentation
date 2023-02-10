/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vaadin;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Vaadin16Test extends AbstractVaadin16Test {

  @Override
  protected void prepareVaadinBaseDir(File baseDir) {
    URL lockFile = AbstractVaadin16Test.class.getResource("/pnpm-lock.yaml");
    if (lockFile == null) {
      return;
    }
    URI uri;
    try {
      uri = lockFile.toURI();
    } catch (URISyntaxException e) {
      throw new IllegalStateException(e);
    }
    Path sourceDirectory = Paths.get(uri).getParent();
    String sourcePath = sourceDirectory.toString();
    Path destinationDirectory = Paths.get(baseDir.toURI());
    String destinationPath = destinationDirectory.toString();
    try (Stream<Path> stream = Files.walk(sourceDirectory)) {
      stream.forEach(
          source -> {
            Path destination =
                Paths.get(destinationPath, source.toString().substring(sourcePath.length()));
            if (!Files.exists(destination)) {
              try {
                Files.copy(source, destination);
              } catch (IOException e) {
                throw new IllegalStateException(e);
              }
            }
          });
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
