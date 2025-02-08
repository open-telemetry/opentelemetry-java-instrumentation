plugins {
  id("otel.java-conventions")
}

dependencies {
  implementation("com.google.api-client:google-api-client:2.7.2")
  implementation("com.google.apis:google-api-services-sheets:v4-rev20250106-2.0.0")
  implementation("com.google.auth:google-auth-library-oauth2-http:1.32.1")
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

tasks {
  val reportFlakyTests by registering(JavaExec::class) {
    dependsOn(classes)

    mainClass.set("io.opentelemetry.instrumentation.testreport.FlakyTestReporter")
    classpath(sourceSets["main"].runtimeClasspath)

    systemProperty("scanPath", project.rootDir)
    systemProperty("googleSheetsAccessKey", System.getenv("FLAKY_TEST_REPORTER_ACCESS_KEY"))
    systemProperty("buildScanUrl", System.getenv("BUILD_SCAN_URL"))
    systemProperty("jobUrl", System.getenv("JOB_URL"))
  }
}
