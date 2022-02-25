## Rationale for not supporting Java 7

### Android support is no longer tied to Java 7

Even for supporting old Android API levels:

> If you're building your app using Android Gradle plugin 4.0.0 or higher, the plugin extends
> support for using a number of Java 8 language APIs <b>without requiring a minimum API level for
> your app</b>.

(https://developer.android.com/studio/write/java8-support#library-desugaring)

There are some Java 8 APIs that Android does not desugar, but we can use
[animal sniffer plugin](https://github.com/xvik/gradle-animalsniffer-plugin) to ensure we don't use
those particular Java 8 APIs that are not available in the base Android level we decide to support,
e.g. OkHttp takes this approach to
[ensure compliance with Android API level 21](https://github.com/square/okhttp/blob/96a2118dd447ebc28a64d9b11a431ca642edc441/build.gradle#L144-L153)

We will use this approach for the `instrumentation-api` module and for any library (manual)
instrumentation that would be useful to Android developers
(e.g. library instrumentation for OkHttp).

### Modern test tooling requires Java 8+

Both JUnit 5 and Testcontainers require Java 8+.

### Auto-instrumentation (Javaagent)

We could run tests against Java 8+ and ensure Java 7 compliance by using similar animal sniffer
technique as above.

But bytecode instrumentation tends to be much more sensitive to Java versions than normal code, and
we would lose a lot of confidence in the quality of our Java 7 support without being able to run our
standard tests against it.

Another option would be to run the "code under test" in a separate JVM from our test harness, which
would allow us to use Java 8 for our test harness (e.g. use JUnit 5 and Testcontainers), while
running our "code under test" inside of Java 7. This is an attractive approach (and e.g. Glowroot
does this, though not to run on older JVMs, but to run with the `-javaagent` flag because I didn't
think about hacking the `-javaagent` flag directly into the test JVM). But this approach does come
with a more complex testing and debugging story due to propagating tests and parameters, and
debugging across two separate JVMs. And new contributor experience has a very high priority for this
project (compared to say commercial tools who can invest more in onboarding their employees onto a
more complex codebase).

### Library (manual) instrumentation

We believe that Java 7 users are primarily in maintenance mode and not interested in cracking open
their code anymore and adding library (manual) instrumentation, so we don't believe there is much
interest in library instrumentation targeting Java 7.

### Java 7 usage

Certainly one factor to consider is what percentage of production applications are running Java 7.

Luckily, New Relic
[published their numbers recently](https://blog.newrelic.com/technology/state-of-java),
so we know that ~2.5% of production applications are still running Java 7 as of March 2020.

### Alternatives for Java 7 users

We understand the situations that lead applications to get stuck on Java 7 (we've been there
ourselves), and we agree that those applications need monitoring too.

Our decision may have been different if those Java 7 users did not have any other alternative
for codeless monitoring, but there are many existing codeless monitoring solutions that still
support Java 7 (both open source and commercial), and probably many of those applications, having
been in production for a long time already, are already using one of those solutions.
