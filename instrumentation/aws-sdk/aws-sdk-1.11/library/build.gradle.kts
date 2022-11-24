plugins {
  id("otel.library-instrumentation")
}

dependencies {
  implementation("io.opentelemetry.contrib:opentelemetry-aws-xray-propagator")

  library("com.amazonaws:aws-java-sdk-core:1.11.0")

  testImplementation(project(":instrumentation:aws-sdk:aws-sdk-1.11:testing"))

  testLibrary("com.amazonaws:aws-java-sdk-s3:1.11.106")
  testLibrary("com.amazonaws:aws-java-sdk-rds:1.11.106")
  testLibrary("com.amazonaws:aws-java-sdk-ec2:1.11.106")
  testLibrary("com.amazonaws:aws-java-sdk-kinesis:1.11.106")
  testLibrary("com.amazonaws:aws-java-sdk-dynamodb:1.11.106")
  testLibrary("com.amazonaws:aws-java-sdk-sns:1.11.106")
  testLibrary("com.amazonaws:aws-java-sdk-sqs:1.11.106")
}
