/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.auto.javaclassloader;

import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.extendsClass;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.HelperResources;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Instruments {@link ClassLoader} to have calls to get resources intercepted and check our map of
 * helper resources that is filled by instrumentation when they need helpers.
 */
@AutoService(Instrumenter.class)
public class ResourceInjectionInstrumentation extends Instrumenter.Default {

  public ResourceInjectionInstrumentation() {
    super("class-loader");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "io.opentelemetry.instrumentation.auto.javaclassloader.BytesUtil",
    };
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return extendsClass(named("java.lang.ClassLoader"));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        isMethod().and(named("getResource")).and(takesArguments(String.class)),
        ResourceInjectionInstrumentation.class.getName() + "$GetResourceAdvice");

    transformers.put(
        isMethod().and(named("getResources")).and(takesArguments(String.class)),
        ResourceInjectionInstrumentation.class.getName() + "$GetResourcesAdvice");
    return transformers;
  }

  public static class GetResourceAdvice {
    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void onExit(
        @Advice.This ClassLoader classLoader,
        @Advice.Argument(0) String name,
        @Advice.Return(readOnly = false) URL resourceUrl) {
      if (resourceUrl != null) {
        // Give their classloader precedence.
        return;
      }

      resourceUrl = HelperResources.load(classLoader, name);
    }
  }

  public static class GetResourcesAdvice {
    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void onExit(
        @Advice.This ClassLoader classLoader,
        @Advice.Argument(0) String name,
        @Advice.Return(readOnly = false) Enumeration<URL> resources) {
      URL helper = HelperResources.load(classLoader, name);
      if (helper == null) {
        return;
      }

      if (!resources.hasMoreElements()) {
        resources = Collections.enumeration(Collections.singleton(helper));
        return;
      }

      // TODO(anuraaga): Don't inline BytesUtil.toByteArray after understanding AccessError when
      // trying to use the class from bootstrap classloader.
      byte[] buf = new byte[8192];
      try {
        InputStream is = helper.openStream();
        ByteArrayOutputStream os = new ByteArrayOutputStream(is.available());
        while (true) {
          int r = is.read(buf);
          if (r == -1) {
            break;
          }
          os.write(buf, 0, r);
        }
        byte[] injected = os.toByteArray();

        List<URL> result = Collections.list(resources);
        for (URL loadedUrl : result) {
          is = loadedUrl.openStream();
          os = new ByteArrayOutputStream(is.available());
          while (true) {
            int r = is.read(buf);
            if (r == -1) {
              break;
            }
            os.write(buf, 0, r);
          }

          byte[] loaded = os.toByteArray();
          if (Arrays.equals(injected, loaded)) {
            // Same resource already present, don't inject a duplicate.
            resources = Collections.enumeration(result);
            return;
          }
        }
        result.add(helper);
        resources = Collections.enumeration(result);
      } catch (IOException e) {
        // Shouldn't happen but ignore if it does.
      }
    }
  }
}
