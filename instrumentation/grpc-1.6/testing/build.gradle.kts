import com.google.protobuf.gradle.*

plugins {
  id("otel.java-conventions")
  id("com.google.protobuf")
}

val grpcVersion = "1.6.0"

dependencies {
  api(project(":testing-common"))

  api("io.grpc:grpc-core:$grpcVersion")
  api("io.grpc:grpc-protobuf:$grpcVersion")
  api("io.grpc:grpc-services:$grpcVersion")
  api("io.grpc:grpc-stub:$grpcVersion")

  implementation("javax.annotation:javax.annotation-api:1.3.2")

  implementation("com.google.guava:guava")

  api("org.junit-pioneer:junit-pioneer")
  implementation("io.opentelemetry:opentelemetry-api")
}

tasks {
  compileJava {
    with(options) {
      // We generate stubs using an old version of protobuf to test old versions of gRPC,
      // where this lint error triggers.
      compilerArgs.add("-Xlint:-cast")
    }
  }
}

protobuf {
  protoc {
    // The artifact spec for the Protobuf Compiler
    artifact = "com.google.protobuf:protoc:3.3.0"
    if (osdetector.os == "osx") {
      // Always use x86_64 version as ARM binary is not available
      artifact += ":osx-x86_64"
    }
  }
  plugins {
    id("grpc") {
      artifact = "io.grpc:protoc-gen-grpc-java:$grpcVersion"
      if (osdetector.os == "osx") {
        // Always use x86_64 version as ARM binary is not available
        artifact += ":osx-x86_64"
      }
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

afterEvaluate {
  // Classpath when compiling protos, we add dependency management directly
  // since it doesn't follow Gradle conventions of naming / properties.
  dependencies {
    add("compileProtoPath", platform(project(":dependencyManagement")))
    add("testCompileProtoPath", platform(project(":dependencyManagement")))
  }
}
