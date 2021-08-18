package io.opentelemetry.javaagent.tooling.muzzle;

import io.opentelemetry.javaagent.extension.instrumentation.HelperResources;
import java.util.HashMap;
import java.util.Map;

public class HelperResourcesImpl implements HelperResources {

  private final Map<String, String> resourceNameMappings = new HashMap<>();

  @Override
  public void register(String resourceName) {
    resourceNameMappings.put(resourceName, resourceName);
  }

  @Override
  public void register(String resourceName, String resourceNameForContent) {
    resourceNameMappings.put(resourceName, resourceNameForContent);
  }

  /**
   * Returns the registered mappings, where the mapping key is the name of the resource to inject in
   * to the user's class loader, and the value is name of the instrumentation resource which
   * provides the content for injection.
   */
  public Map<String, String> getResourceNameMappings() {
    return resourceNameMappings;
  }
}
