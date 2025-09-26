/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.auditors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.docs.utils.FileManager;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class SupportedLibrariesAuditorTest {

  @Test
  void testPerformAuditWithNoMissingItems() throws IOException, InterruptedException {
    HttpClient mockClient = mock(HttpClient.class);
    HttpResponse<String> mockResponse = mock(HttpResponse.class);
    when(mockClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
        .thenReturn(mockResponse);
    when(mockResponse.statusCode()).thenReturn(200);
    when(mockResponse.body()).thenReturn(createRemoteSupportedLibrariesContent());

    try (MockedStatic<FileManager> fileManagerMock = Mockito.mockStatic(FileManager.class)) {
      fileManagerMock
          .when(() -> FileManager.readFileToString(any()))
          .thenReturn(createLocalSupportedLibrariesContent());

      SupportedLibrariesAuditor auditor = new SupportedLibrariesAuditor();
      Optional<String> result = auditor.performAudit(mockClient);

      assertThat(result).isEmpty();
    }
  }

  @Test
  void testPerformAuditWithMissingItems() throws IOException, InterruptedException {
    HttpClient mockClient = mock(HttpClient.class);
    HttpResponse<String> mockResponse = mock(HttpResponse.class);
    when(mockClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
        .thenReturn(mockResponse);
    when(mockResponse.statusCode()).thenReturn(200);
    when(mockResponse.body()).thenReturn(createRemoteSupportedLibrariesContentMissing());

    try (MockedStatic<FileManager> fileManagerMock = Mockito.mockStatic(FileManager.class)) {
      fileManagerMock
          .when(() -> FileManager.readFileToString(any()))
          .thenReturn(createLocalSupportedLibrariesContent());

      SupportedLibrariesAuditor auditor = new SupportedLibrariesAuditor();
      Optional<String> result = auditor.performAudit(mockClient);

      assertThat(result).isPresent();
      assertThat(result.get())
          .contains("Missing Supported Libraries (1 item(s) missing from remote):");
      assertThat(result.get()).contains("- Apache Camel");
    }
  }

  private static String createLocalSupportedLibrariesContent() {
    return
"""
# Supported libraries, frameworks, application servers, and JVMs

We automatically instrument and support a huge number of libraries, frameworks,
and application servers... right out of the box!

## Contents

- [Libraries / Frameworks](#libraries--frameworks)
- [Application Servers](#application-servers)

## Libraries / Frameworks

These are the supported libraries and frameworks:

| Library/Framework                                                                                                                           | Auto-instrumented versions         | Standalone Library Instrumentation [1] | Semantic Conventions                                                                                                                              |
|---------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------|
| [ActiveJ](https://activej.io/)                                                                                                              | 6.0+                               | N/A                                                                                                                                                                                                                                                                                                                                                                                     | [HTTP Server Spans], [HTTP Server Metrics]                                                                                                        |
| [Akka Actors](https://doc.akka.io/docs/akka/current/typed/index.html)                                                                       | 2.3+                               | N/A                                                                                                                                                                                                                                                                                                                                                                                     | Context propagation                                                                                                                               |
| [Apache Camel](https://camel.apache.org/)                                                                                                   | 2.20+ (not including 3.0+ yet)     | N/A                                                                                                                                                                                                                                                                                                                                                                                     | Dependent on components in use                                                                                                                    |

## Application Servers

These are the application servers that the smoke tests are run against:

| Application server                                                                    | Version                                  | JVM                                                    | OS                                    |
|---------------------------------------------------------------------------------------|------------------------------------------|--------------------------------------------------------|---------------------------------------|
| [Jetty](https://www.eclipse.org/jetty/)                                               | 9.4.53                                   | OpenJDK 8, 11, 17, 21, 23<br/>OpenJ9 8, 11, 17, 21, 23 | [`ubuntu-latest`], [`windows-latest`] |
""";
  }

  private static String createRemoteSupportedLibrariesContent() {
    return
"""
# Supported libraries, frameworks, application servers, and JVMs

We automatically instrument and support a huge number of libraries, frameworks,
and application servers... right out of the box!

## Contents

- [Libraries / Frameworks](#libraries--frameworks)
- [Application Servers](#application-servers)

## Libraries / Frameworks

These are the supported libraries and frameworks:

| Library/Framework                                                                                                                           | Auto-instrumented versions         | Standalone Library Instrumentation [1] | Semantic Conventions                                                                                                                              |
|---------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------|
| [ActiveJ](https://activej.io/)                                                                                                              | 6.0+                               | N/A                                                                                                                                                                                                                                                                                                                                                                                     | [HTTP Server Spans], [HTTP Server Metrics]                                                                                                        |
| [Akka Actors](https://doc.akka.io/docs/akka/current/typed/index.html)                                                                       | 2.3+                               | N/A                                                                                                                                                                                                                                                                                                                                                                                     | Context propagation                                                                                                                               |
| [Apache Camel](https://camel.apache.org/)                                                                                                   | 2.20+ (not including 3.0+ yet)     | N/A                                                                                                                                                                                                                                                                                                                                                                                     | Dependent on components in use                                                                                                                    |

## Application Servers

These are the application servers that the smoke tests are run against:

| Application server                                                                    | Version                                  | JVM                                                    | OS                                    |
|---------------------------------------------------------------------------------------|------------------------------------------|--------------------------------------------------------|---------------------------------------|
| [Jetty](https://www.eclipse.org/jetty/)                                               | 9.4.53                                   | OpenJDK 8, 11, 17, 21, 23<br/>OpenJ9 8, 11, 17, 21, 23 | [`ubuntu-latest`], [`windows-latest`] |
""";
  }

  private static String createRemoteSupportedLibrariesContentMissing() {
    return
"""
# Supported libraries, frameworks, application servers, and JVMs

We automatically instrument and support a huge number of libraries, frameworks,
and application servers... right out of the box!

## Contents

- [Libraries / Frameworks](#libraries--frameworks)
- [Application Servers](#application-servers)

## Libraries / Frameworks

These are the supported libraries and frameworks:

| Library/Framework                                                                                                                           | Auto-instrumented versions         | Standalone Library Instrumentation [1] | Semantic Conventions                                                                                                                              |
|---------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------|
| [ActiveJ](https://activej.io/)                                                                                                              | 6.0+                               | N/A                                                                                                                                                                                                                                                                                                                                                                                     | [HTTP Server Spans], [HTTP Server Metrics]                                                                                                        |
| [Akka Actors](https://doc.akka.io/docs/akka/current/typed/index.html)                                                                       | 2.3+                               | N/A                                                                                                                                                                                                                                                                                                                                                                                     | Context propagation                                                                                                                               |

## Application Servers

These are the application servers that the smoke tests are run against:

| Application server                                                                    | Version                                  | JVM                                                    | OS                                    |
|---------------------------------------------------------------------------------------|------------------------------------------|--------------------------------------------------------|---------------------------------------|
| [Jetty](https://www.eclipse.org/jetty/)                                               | 9.4.53                                   | OpenJDK 8, 11, 17, 21, 23<br/>OpenJ9 8, 11, 17, 21, 23 | [`ubuntu-latest`], [`windows-latest`] |
""";
  }
}
