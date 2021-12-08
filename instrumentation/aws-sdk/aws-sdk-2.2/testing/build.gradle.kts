plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":testing-common"))

  api("software.amazon.awssdk:apache-client:2.2.0")
  // older versions don't play nice with armeria http server
  api("software.amazon.awssdk:netty-nio-client:2.11.0")
  api("software.amazon.awssdk:s3:2.2.0")
  api("software.amazon.awssdk:rds:2.2.0")
  api("software.amazon.awssdk:ec2:2.2.0")
  api("software.amazon.awssdk:sqs:2.2.0")
  api("software.amazon.awssdk:dynamodb:2.2.0")
  api("software.amazon.awssdk:kinesis:2.2.0")

  implementation("com.google.guava:guava")

  implementation("org.codehaus.groovy:groovy-all")
  implementation("io.opentelemetry:opentelemetry-api")
  implementation("org.spockframework:spock-core")
}
