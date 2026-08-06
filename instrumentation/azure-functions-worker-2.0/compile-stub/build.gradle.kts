plugins {
  id("otel.java-conventions")
}

// azure-functions-java-worker is not published to maven central, so we provide stripped down
// versions of the worker classes that we compile against
