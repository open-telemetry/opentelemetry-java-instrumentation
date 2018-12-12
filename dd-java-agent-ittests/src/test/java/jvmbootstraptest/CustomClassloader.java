package jvmbootstraptest;

import java.net.URL;
import java.net.URLClassLoader;

public class CustomClassloader extends URLClassLoader {
  static {
    ClassLoader.registerAsParallelCapable();
  }

  public CustomClassloader(ClassLoader parent) {
    this(new URL[0], parent);
  }

  public CustomClassloader(URL[] urls, ClassLoader parent) {
    super(urls, parent);
  }

  @Override
  public Object getClassLoadingLock(String className) {
    return super.getClassLoadingLock(className);
  }
}
