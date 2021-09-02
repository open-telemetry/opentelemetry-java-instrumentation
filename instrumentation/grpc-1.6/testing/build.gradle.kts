import com.google.protobuf.gradle.generateProtoTasks
import com.google.protobuf.gradle.id
import com.google.protobuf.gradle.plugins
import com.google.protobuf.gradle.protobuf
import com.google.protobuf.gradle.protoc

plugins {
  id("otel.java-conventions")
  id("com.google.protobuf") version "0.8.16"
}

val grpcVersion = "1.6.0"

protobuf {
  protoc {
    // Download compiler rather than using locally installed version:
    artifact = "com.google.protobuf:protoc:3.3.0"
  }
  plugins {
    id("grpc") {
      artifact = "io.grpc:protoc-gen-grpc-java:$grpcVersion"
    }
  }
  generateProtoTasks {
    all().configureEach {
      plugins {
        id("grpc")
      }
    }
  }
}

dependencies {
  api(project(":testing-common"))

  api("io.grpc:grpc-core:$grpcVersion")
  api("io.grpc:grpc-protobuf:$grpcVersion")
  api("io.grpc:grpc-services:$grpcVersion")
  api("io.grpc:grpc-stub:$grpcVersion")

  implementation("javax.annotation:javax.annotation-api:1.3.2")

  implementation("com.google.guava:guava")

  implementation("org.codehaus.groovy:groovy-all")
  implementation("io.opentelemetry:opentelemetry-api")
  implementation("org.spockframework:spock-core")
}
