plugins {
  id("otel.library-instrumentation")
}

dependencies {
  implementation(project(":instrumentation:aws-lambda:aws-lambda-core-1.0:library"))

  compileOnly("io.opentelemetry:opentelemetry-sdk")
  compileOnly("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  library("com.amazonaws:aws-lambda-java-core:1.0.0")
  // First version to includes support for SQSEvent, currently the most popular message queue used
  // with lambda.
  // NB: 2.2.0 includes a class called SQSEvent but isn't usable due to it returning private classes
  // in public API.
  library("com.amazonaws:aws-lambda-java-events:2.2.1")

  implementation("com.fasterxml.jackson.core:jackson-databind")
  implementation("io.opentelemetry:opentelemetry-extension-aws")

  // allows to get the function ARN
  testLibrary("com.amazonaws:aws-lambda-java-core:1.2.1")
  // allows to get the default events
  testLibrary("com.amazonaws:aws-lambda-java-events:3.10.0")

  testImplementation("com.fasterxml.jackson.core:jackson-databind")
  testImplementation("commons-io:commons-io:2.2")

  testImplementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")
  testImplementation("io.opentelemetry:opentelemetry-extension-trace-propagators")
  testImplementation("com.google.guava:guava")

  testImplementation(project(":instrumentation:aws-lambda:aws-lambda-events-2.2:testing"))
  testImplementation("org.mockito:mockito-core")
  testImplementation("org.assertj:assertj-core")
  testImplementation("uk.org.webcompere:system-stubs-jupiter")
}
