plugins {
  id("otel.java-conventions")
}

// liberty jars are not available as a maven dependency so we provide stripped
// down versions of liberty classes that we can use from integration code without
// having to do everything with reflection

// disable checkstyle
// Abbreviation in name 'getRequestURI' must contain no more than '2' consecutive capital letters. [AbbreviationAsWordInName]
tasks {
  named("checkstyleMain") {
    enabled = false
  }
}
