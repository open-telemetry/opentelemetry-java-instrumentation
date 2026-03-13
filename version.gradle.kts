val stableVersion = "2.26.0"
val alphaVersion = "2.26.0-alpha"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
}
