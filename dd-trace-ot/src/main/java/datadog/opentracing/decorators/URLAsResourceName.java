package datadog.opentracing.decorators;

import datadog.opentracing.DDSpanContext;
import datadog.trace.api.DDTags;
import io.opentracing.tag.Tags;
import java.util.regex.Pattern;

public class URLAsResourceName extends AbstractDecorator {

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
    if (value == null || statusCode != null && statusCode.equals("404")) {
      return true;
    }

    final String rawPath = rawPathFromUrlString(String.valueOf(value).trim());
    final String normalizedPath = normalizePath(rawPath);
    final String resourceName = addMethodIfAvailable(context, normalizedPath);

    context.setResourceName(resourceName);
    return true;
  }

  private String rawPathFromUrlString(final String url) {
    // Get the path without host:port
    // url may already be just the path.

    if (url.isEmpty()) {
      return "/";
    }

    final int queryLoc = url.indexOf("?");
    final int fragmentLoc = url.indexOf("#");
    final int endLoc;
    if (queryLoc < 0) {
      if (fragmentLoc < 0) {
        endLoc = url.length();
      } else {
        endLoc = fragmentLoc;
      }
    } else {
      if (fragmentLoc < 0) {
        endLoc = queryLoc;
      } else {
        endLoc = Math.min(queryLoc, fragmentLoc);
      }
    }

    final int protoLoc = url.indexOf("://");
    if (protoLoc < 0) {
      return url.substring(0, endLoc);
    }

    final int pathLoc = url.indexOf("/", protoLoc + 3);
    if (pathLoc < 0) {
      return "/";
    }

    if (queryLoc < 0) {
      return url.substring(pathLoc);
    } else {
      return url.substring(pathLoc, endLoc);
    }
  }

  // Method to normalise the url string
  private String normalizePath(final String path) {
    if (path.isEmpty() || path.equals("/")) {
      return "/";
    }

    return PATH_MIXED_ALPHANUMERICS.matcher(path).replaceAll("?");
  }

  private String addMethodIfAvailable(final DDSpanContext context, String path) {
    // if the verb (GET, POST ...) is present, add it
    final String verb = (String) context.getTags().get(Tags.HTTP_METHOD.getKey());
    if (verb != null && !verb.isEmpty()) {
      path = verb + " " + path;
    }
    return path;
  }
}
