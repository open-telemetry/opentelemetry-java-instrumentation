plugins {
  id("otel.library-instrumentation")
}

dependencies {
  compileOnly("io.opentelemetry:opentelemetry-sdk")
  compileOnly("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  library("com.amazonaws:aws-lambda-java-core:1.0.0")

  implementation("io.opentelemetry:opentelemetry-extension-aws")

  // We do lightweight parsing of JSON to extract HTTP headers from requests for propagation.
  // This will be commonly needed even for users that don't use events, but luckily it's not too big.
  // Note that Lambda itself uses Jackson, but does not expose it to the function so we need to include
  // it here.
  implementation("com.fasterxml.jackson.core:jackson-core")

  // allows to get the function ARN
  testLibrary("com.amazonaws:aws-lambda-java-core:1.2.1")

  testImplementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")
  testImplementation("io.opentelemetry:opentelemetry-extension-trace-propagators")
  testImplementation("com.google.guava:guava")

  testImplementation(project(":instrumentation:aws-lambda:aws-lambda-core-1.0:testing"))
  testImplementation("org.mockito:mockito-core")
  testImplementation("org.assertj:assertj-core")
  testImplementation("uk.org.webcompere:system-stubs-jupiter")
}
