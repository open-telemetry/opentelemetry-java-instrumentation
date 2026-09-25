/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.geode.v1_4;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.geode.cache.Cache;
import org.apache.geode.cache.CacheFactory;
import org.apache.geode.cache.RegionShortcut;
import org.apache.geode.cache.server.CacheServer;

public final class GeodeServerMain {

  public static void main(String[] args) throws Exception {
    Path portFile = Paths.get(args[0]);

    try (Cache cache = new CacheFactory().set("locators", "").set("mcast-port", "0").create()) {
      cache.createRegionFactory(RegionShortcut.REPLICATE).create("test-region");

      CacheServer server = cache.addCacheServer();
      server.setPort(0);
      server.start();

      Path temporaryPortFile = portFile.resolveSibling(portFile.getFileName() + ".tmp");
      Files.write(temporaryPortFile, Integer.toString(server.getPort()).getBytes(UTF_8));
      Files.move(temporaryPortFile, portFile, ATOMIC_MOVE, REPLACE_EXISTING);

      while (System.in.read() != -1) {}
    }
  }

  private GeodeServerMain() {}
}
