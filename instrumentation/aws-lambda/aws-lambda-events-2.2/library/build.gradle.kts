plugins {
  id("otel.library-instrumentation")
}

dependencies {
  api(project(":instrumentation:aws-lambda:aws-lambda-core-1.0:library"))

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

  // By default, "aws-lambda-java-serialization" library is enabled in the classpath
  // at the AWS Lambda environment except "java8" runtime which is deprecated.
  // But it is available at "java8.al2" runtime, so it is still can be used
  // by Java 8 based Lambda functions.
  // So that is the reason that why we add it as compile only dependency.
  compileOnly("com.amazonaws:aws-lambda-java-serialization:1.1.5")

  // We need Jackson for wrappers to reproduce the serialization does when Lambda invokes a RequestHandler with event
  // since Lambda will only be able to invoke the wrapper itself with a generic Object.
  // Note that Lambda itself uses Jackson, but does not expose it to the function so we need to include it here.
  // TODO: Switch to aws-lambda-java-serialization to more robustly follow Lambda's serialization logic.
  implementation("com.fasterxml.jackson.core:jackson-databind")
  implementation("io.opentelemetry.contrib:opentelemetry-aws-xray-propagator")

  // allows to get the function ARN
  testLibrary("com.amazonaws:aws-lambda-java-core:1.2.1")
  // allows to get the default events
  testLibrary("com.amazonaws:aws-lambda-java-events:3.10.0")

  testImplementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")
  testImplementation("io.opentelemetry:opentelemetry-extension-trace-propagators")
  testImplementation("com.google.guava:guava")
  testImplementation("com.amazonaws:aws-lambda-java-serialization:1.1.5")

  testImplementation(project(":instrumentation:aws-lambda:aws-lambda-events-2.2:testing"))
  testImplementation("uk.org.webcompere:system-stubs-jupiter")
}

tasks.withType<Test>().configureEach {
  // required on jdk17
  jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
  jvmArgs("--add-opens=java.base/java.util=ALL-UNNAMED")
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
}
