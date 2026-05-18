val stableVersion = "2.28.0"
val alphaVersion = "2.28.0-alpha"

val apidiffBaselineVersion = "2.27.0"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
  extra["apidiffBaselineVersion"] = apidiffBaselineVersion
}
