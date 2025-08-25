plugins {
  id("otel.library-instrumentation")
}

dependencies {
  implementation("io.opentelemetry.contrib:opentelemetry-aws-xray-propagator")

  library("com.amazonaws:aws-java-sdk-core:1.11.0")
  library("com.amazonaws:aws-java-sdk-sqs:1.11.106")
  compileOnly(project(":muzzle"))

  testImplementation(project(":instrumentation:aws-sdk:aws-sdk-1.11:testing"))

  testLibrary("com.amazonaws:aws-java-sdk-dynamodb:1.11.106")
  testLibrary("com.amazonaws:aws-java-sdk-ec2:1.11.106")
  testLibrary("com.amazonaws:aws-java-sdk-kinesis:1.11.106")
  testLibrary("com.amazonaws:aws-java-sdk-lambda:1.11.106")
  testLibrary("com.amazonaws:aws-java-sdk-rds:1.11.106")
  testLibrary("com.amazonaws:aws-java-sdk-s3:1.11.106")
  testLibrary("com.amazonaws:aws-java-sdk-sns:1.11.106")
  testLibrary("com.amazonaws:aws-java-sdk-stepfunctions:1.11.106")

  // last version that does not use json protocol
  latestDepTestLibrary("com.amazonaws:aws-java-sdk-sqs:1.12.583") // documented limitation
}

val testLatestDeps = findProperty("testLatestDeps") as Boolean
if (!testLatestDeps) {
  configurations.testRuntimeClasspath {
    resolutionStrategy {
      eachDependency {
        // early versions of aws sdk are not compatible with jackson 2.16.0
        if (requested.group.startsWith("com.fasterxml.jackson")) {
          useVersion("2.15.3")
        }
      }
    }
  }
}

testing {
  suites {
    val testSecretsManager by registering(JvmTestSuite::class) {
      dependencies {
        implementation(project())
        implementation(project(":instrumentation:aws-sdk:aws-sdk-1.11:testing"))
        val version = if (testLatestDeps) "latest.release" else "1.12.80"
        implementation("com.amazonaws:aws-java-sdk-secretsmanager:$version")
      }
    }
  }
}

tasks {
  val testStableSemconv by registering(Test::class) {
    jvmArgs("-Dotel.semconv-stability.opt-in=database")
  }

  check {
    dependsOn(testing.suites)
    dependsOn(testStableSemconv)
  }
}
