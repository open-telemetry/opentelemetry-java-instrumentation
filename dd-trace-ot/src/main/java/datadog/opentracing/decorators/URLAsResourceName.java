package datadog.opentracing.decorators;

import datadog.opentracing.DDSpanContext;
import datadog.trace.api.DDTags;
import io.opentracing.tag.Tags;
import java.net.MalformedURLException;
import java.util.regex.Pattern;

/** Decorator for servlet contrib */
public class URLAsResourceName extends AbstractDecorator {

  // Matches everything after the ? character.
  public static final Pattern QUERYSTRING = Pattern.compile("\\?.*$");
  // Matches any path segments with numbers in them. (exception for versioning: "/v1/")
  public static final Pattern PATH_MIXED_ALPHANUMERICS =
      Pattern.compile("(?<=/)(?![vV]\\d{1,2}/)(?:[^\\/\\d\\?]*[\\d]+[^\\/\\?]*)");

  public URLAsResourceName() {
    super();
    setMatchingTag(Tags.HTTP_URL.getKey());
    setReplacementTag(DDTags.RESOURCE_NAME);
  }

  @Override
  public boolean shouldSetTag(final DDSpanContext context, final String tag, final Object value) {
    final String statusCode = String.valueOf(context.getTags().get(Tags.HTTP_STATUS.getKey()));
    // do nothing if the status code is already set and equals to 404.
    // TODO: it assumes that Status404Decorator is active. If it's not, it will lead to unexpected
    // behaviors
    if (statusCode != null && statusCode.equals("404")) {
      return true;
    }

    // Get the path without host:port
    String path = String.valueOf(value);

    try {
      path = new java.net.URL(path).getPath();
    } catch (final MalformedURLException e) {
      // do nothing, use the value instead of the path
    }
    // normalize the path
    path = norm(path);

    // if the verb (GET, POST ...) is present, add it
    final String verb = (String) context.getTags().get(Tags.HTTP_METHOD.getKey());
    if (verb != null && !verb.isEmpty()) {
      path = verb + " " + path;
    }

    context.setResourceName(path);
    return true;
  }

  // Method to normalise the url string
  private String norm(final String origin) {

    String norm = origin;
    norm = QUERYSTRING.matcher(norm).replaceAll("");
    norm = PATH_MIXED_ALPHANUMERICS.matcher(norm).replaceAll("?");

    if (norm.trim().isEmpty()) {
      norm = "/";
    }

    return norm;
  }
}
