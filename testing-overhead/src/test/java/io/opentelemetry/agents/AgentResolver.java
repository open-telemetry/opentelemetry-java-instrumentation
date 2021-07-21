/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.opentelemetry.agents;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class AgentResolver {

  final static String OTEL_LATEST = "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent-all.jar";

  private final LatestAgentSnapshotResolver snapshotResolver = new LatestAgentSnapshotResolver();

  public Optional<Path> resolve(String agent) throws IOException {
    switch (agent) {
      case "none":
        return Optional.empty();
      case "release":
        return Optional.of(downloadLatestStable());
      case "snapshot":
        return snapshotResolver.resolve();
    }
    // TODO: Resolve explicit https:// and file:/// urls
    throw new IllegalArgumentException("Unknown agent: " + agent);
  }

  Path downloadLatestStable() throws IOException {
    Request request = new Request.Builder().url(OTEL_LATEST).build();
    OkHttpClient client = new OkHttpClient();
    Response response = client.newCall(request).execute();
    byte[] raw = response.body().bytes();
    Path path = Paths.get(".", "opentelemetry-javaagent-all.jar");
    FileOutputStream out = new FileOutputStream(path.toFile());
    out.write(raw);
    out.flush();
    out.close();
    return path;
  }

  // TODO: Download latest snapshot

}
