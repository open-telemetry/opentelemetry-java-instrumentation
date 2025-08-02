/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.agents;

import static org.joox.JOOX.$;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.time.Duration;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.joox.Match;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

public class LatestAgentSnapshotResolver {

  private static final Logger logger = LoggerFactory.getLogger(LatestAgentSnapshotResolver.class);

  static final String BASE_URL =
      "https://oss.sonatype.org/content/repositories/snapshots/io/opentelemetry/javaagent/opentelemetry-javaagent";
  static final String LATEST_SNAPSHOT_META = BASE_URL + "/maven-metadata.xml";

  private static final OkHttpClient client = new OkHttpClient.Builder()
      .connectTimeout(Duration.ofMinutes(1))
      .readTimeout(Duration.ofMinutes(1))
      .build();

  Optional<Path> resolve() throws IOException {
    String version = fetchLatestSnapshotVersion();
    logger.info("Latest snapshot version is {}", version);
    String latestFilename = fetchLatestFilename(version);
    String url = BASE_URL + "/" + version + "/" + latestFilename;
    byte[] jarBytes = fetchBodyBytesFrom(url);
    Path path = Paths.get(".", "opentelemetry-javaagent-SNAPSHOT.jar");
    Files.write(
        path,
        jarBytes,
        StandardOpenOption.CREATE,
        StandardOpenOption.WRITE,
        StandardOpenOption.TRUNCATE_EXISTING);
    return Optional.of(path);
  }

  private String fetchLatestFilename(String version) throws IOException {
    String url = BASE_URL + "/" + version + "/maven-metadata.xml";
    String body = fetchBodyStringFrom(url);
    Document document = $(body).document();
    Match match = $(document).xpath("/metadata/versioning/snapshotVersions/snapshotVersion");
    return match.get().stream()
        .filter(
            elem -> {
              Match classifierMatch = $(elem).child("classifier");
              String classifier = classifierMatch == null ? null : classifierMatch.content();
              String extension = $(elem).child("extension").content();
              return "jar".equals(extension) && (classifier == null);
            })
        .map(e -> $(e).child("value").content())
        .findFirst()
        .map(value -> "opentelemetry-javaagent-" + value + ".jar")
        .orElseThrow();
  }

  private String fetchLatestSnapshotVersion() throws IOException {
    String url = LATEST_SNAPSHOT_META;
    String body = fetchBodyStringFrom(url);
    Document document = $(body).document();
    Match match = $(document).xpath("/metadata/versioning/latest");
    return match.get(0).getTextContent();
  }

  private String fetchBodyStringFrom(String url) throws IOException {
    return fetchBodyFrom(url).string();
  }

  private byte[] fetchBodyBytesFrom(String url) throws IOException {
    return fetchBodyFrom(url).bytes();
  }

  // The sonatype repository can be very unreliable, so we retry a few times
  private ResponseBody fetchBodyFrom(String url) throws IOException {
    Request request = new Request.Builder().url(url).build();
    IOException lastException = null;

    for (int attempt = 0; attempt < 3; attempt++) {
      try {
        try (Response response = client.newCall(request).execute()) {
          if (!response.isSuccessful()) {
            throw new IOException("Unexpected HTTP code " + response.code() + " for " + url);
          }
          ResponseBody body = response.body();
          if (body != null) {
            byte[] data = body.bytes();
            return ResponseBody.create(data, body.contentType());
          } else {
            throw new IOException("Response body is null");
          }
        }
      } catch (IOException e) {
        lastException = e;
        if (attempt < 2) {
          logger.warn("Attempt {} to fetch {} failed: {}. Retrying...", attempt + 1, url, e.getMessage());
        }
      }
    }
    throw lastException;
  }
}

