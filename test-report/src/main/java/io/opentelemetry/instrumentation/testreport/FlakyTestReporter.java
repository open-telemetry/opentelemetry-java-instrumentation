/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testreport;

import static java.nio.file.FileVisitResult.CONTINUE;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

@SuppressWarnings("SystemOut")
public class FlakyTestReporter {
  // https://docs.google.com/spreadsheets/d/1pfa6Ws980AIFI3kKOeIc51-JGEzakG7hkMl4J9h-Tk0
  private static final String SPREADSHEET_ID = "1pfa6Ws980AIFI3kKOeIc51-JGEzakG7hkMl4J9h-Tk0";

  private int testCount;
  private int skippedCount;
  private int failureCount;
  private int errorCount;
  private final List<FlakyTest> flakyTests = new ArrayList<>();

  private record FlakyTest(
      String testClassName, String testName, String timestamp, String message) {}

  private void addFlakyTest(
      String testClassName, String testName, String timestamp, String message) {
    flakyTests.add(new FlakyTest(testClassName, testName, timestamp, message));
  }

  private static Document parse(Path testReport) {
    try {
      DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
      return builder.parse(testReport.toFile());
    } catch (Exception exception) {
      throw new IllegalStateException("failed to parse test report " + testReport, exception);
    }
  }

  @SuppressWarnings("JavaTimeDefaultTimeZone")
  private void scanTestFile(Path testReport) {
    Document doc = parse(testReport);
    doc.getDocumentElement().normalize();
    testCount += Integer.parseInt(doc.getDocumentElement().getAttribute("tests"));
    skippedCount += Integer.parseInt(doc.getDocumentElement().getAttribute("skipped"));
    int failures = Integer.parseInt(doc.getDocumentElement().getAttribute("failures"));
    failureCount += failures;
    int errors = Integer.parseInt(doc.getDocumentElement().getAttribute("errors"));
    errorCount += errors;
    String timestamp = doc.getDocumentElement().getAttribute("timestamp");

    // there are no flaky tests if there are no failures, skip it
    if (failures == 0 && errors == 0) {
      return;
    }

    // google sheets don't automatically recognize dates with time zone, here we reformat the date
    // so that it wouldn't have the time zone
    TemporalAccessor ta =
        DateTimeFormatter.ISO_DATE_TIME.parseBest(
            timestamp, ZonedDateTime::from, LocalDateTime::from);
    timestamp = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(ta);

    // when test result file was modified more than 20 minutes after the time in the results file
    // then the test results were restored from gradle cache and the test wasn't actually executed
    Instant reportModified = Instant.ofEpochMilli(testReport.toFile().lastModified());
    reportModified = reportModified.minus(20, ChronoUnit.MINUTES);
    Instant testExecuted = null;
    if (ta instanceof ZonedDateTime zonedDateTime) {
      testExecuted = zonedDateTime.toInstant();
    } else if (ta instanceof LocalDateTime localDateTime) {
      testExecuted = localDateTime.toInstant(OffsetDateTime.now().getOffset());
    }
    if (testExecuted != null && reportModified.isAfter(testExecuted)) {
      System.err.println(
          "Ignoring " + testReport + " since it appears to be restored from gradle build cache");
      return;
    }

    class TestCase {
      final String className;
      final String name;
      boolean failed;
      boolean succeeded;
      String message;

      TestCase(String className, String name) {
        this.className = className;
        this.name = name;
      }

      boolean isFlaky() {
        return succeeded && failed;
      }
    }

    Map<String, TestCase> testcaseMap = new HashMap<>();

    NodeList testcaseNodes = doc.getElementsByTagName("testcase");
    for (int i = 0; i < testcaseNodes.getLength(); i++) {
      Node testNode = testcaseNodes.item(i);

      String testClassName = testNode.getAttributes().getNamedItem("classname").getNodeValue();
      String testName = testNode.getAttributes().getNamedItem("name").getNodeValue();
      String testKey = testClassName + "." + testName;
      TestCase testCase =
          testcaseMap.computeIfAbsent(testKey, (s) -> new TestCase(testClassName, testName));
      NodeList childNodes = testNode.getChildNodes();
      boolean failed = false;
      for (int j = 0; j < childNodes.getLength(); j++) {
        Node childNode = childNodes.item(j);
        String nodeName = childNode.getNodeName();
        if ("failure".equals(nodeName) || "error".equals(nodeName)) {
          failed = true;
          // if test fails multiple times we'll use the first failure message
          if (testCase.message == null) {
            String message = getAttributeValue(childNode, "message");
            if (message != null) {
              // compress failure message on a single line
              message = message.replaceAll("\n( )*", " ");
            }
            testCase.message = message;
          }
        }
      }
      if (failed) {
        testCase.failed = true;
      } else {
        testCase.succeeded = true;
      }
    }

    for (TestCase testCase : testcaseMap.values()) {
      if (testCase.isFlaky()) {
        addFlakyTest(testCase.className, testCase.name, timestamp, testCase.message);
      }
    }
  }

  private static String getAttributeValue(Node node, String attributeName) {
    NamedNodeMap attributes = node.getAttributes();
    if (attributes == null) {
      return null;
    }
    Node value = attributes.getNamedItem(attributeName);
    return value != null ? value.getNodeValue() : null;
  }

  private static class BaseFileVisitor<T> extends SimpleFileVisitor<T> {
    @Override
    public FileVisitResult visitFileFailed(T file, IOException exception) {
      System.err.println("Failed to visit " + file.toString());
      exception.printStackTrace();
      return CONTINUE;
    }
  }

  private void scanTestResults(Path buildDir) throws IOException {
    Path testResults = buildDir.resolve("test-results");
    if (!Files.exists(testResults)) {
      return;
    }

    Files.walkFileTree(
        testResults,
        new BaseFileVisitor<>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            String name = file.getFileName().toString();
            if (name.startsWith("TEST-") && name.endsWith(".xml")) {
              scanTestFile(file);
            }

            return CONTINUE;
          }
        });
  }

  private static FlakyTestReporter scan(Path path) throws IOException {
    FlakyTestReporter reporter = new FlakyTestReporter();
    Files.walkFileTree(
        path,
        new BaseFileVisitor<>() {
          @Override
          public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
              throws IOException {
            if (dir.endsWith("build")) {
              reporter.scanTestResults(dir);
              return FileVisitResult.SKIP_SUBTREE;
            }
            if (dir.endsWith("src")) {
              return FileVisitResult.SKIP_SUBTREE;
            }
            return CONTINUE;
          }
        });
    return reporter;
  }

  private void print() {
    System.err.printf(
        "Found %d test, skipped %d, failed %d, errored %d\n",
        testCount, skippedCount, failureCount, errorCount);
    if (!flakyTests.isEmpty()) {
      System.err.printf("Found %d flaky test(s):\n", flakyTests.size());
      for (FlakyTest flakyTest : flakyTests) {
        System.err.println(
            flakyTest.timestamp
                + " "
                + flakyTest.testClassName
                + " "
                + flakyTest.testName
                + " "
                + flakyTest.message);
      }
    }
  }

  // add flaky tests to a google sheet
  private void report(String accessKey, String buildScanUrl, String jobUrl) throws Exception {
    if (flakyTests.isEmpty()) {
      return;
    }

    NetHttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();
    GoogleCredentials credentials =
        GoogleCredentials.fromStream(
                new ByteArrayInputStream(accessKey.getBytes(StandardCharsets.UTF_8)))
            .createScoped(Collections.singletonList(SheetsScopes.SPREADSHEETS));
    Sheets service =
        new Sheets.Builder(
                transport,
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials))
            .setApplicationName("Flaky test reporter")
            .build();

    List<List<Object>> data = new ArrayList<>();
    for (FlakyTest flakyTest : flakyTests) {
      List<Object> row = new ArrayList<>();
      row.add(flakyTest.timestamp);
      row.add(flakyTest.testClassName);
      row.add(flakyTest.testName);
      row.add(buildScanUrl);
      row.add(jobUrl);
      // there is a limit of 50000 characters in a single cell
      row.add(abbreviate(flakyTest.message, 10000));
      data.add(row);
    }

    ValueRange valueRange = new ValueRange();
    valueRange.setValues(data);
    service
        .spreadsheets()
        .values()
        .append(SPREADSHEET_ID, "Sheet1!A:F", valueRange)
        .setValueInputOption("USER_ENTERED")
        .execute();
  }

  private static String abbreviate(String text, int maxLength) {
    if (text.length() > maxLength) {
      return text.substring(0, maxLength - 3) + "...";
    }

    return text;
  }

  public static void main(String... args) throws Exception {
    String path = System.getProperty("scanPath");
    if (path == null) {
      throw new IllegalStateException("scanPath system property must be set");
    }
    File file = new File(path).getAbsoluteFile();
    System.err.println("Scanning for flaky tests in " + file.getPath());
    FlakyTestReporter reporter = FlakyTestReporter.scan(file.toPath());
    reporter.print();

    String accessKey = System.getProperty("googleSheetsAccessKey");
    String buildScanUrl = System.getProperty("buildScanUrl");
    String jobUrl = System.getProperty("jobUrl");
    if (accessKey != null && !accessKey.isEmpty()) {
      reporter.report(accessKey, buildScanUrl, jobUrl);
    }
  }
}
