package com.sun.enterprise.v3.server;

import java.util.HashSet;
import java.util.Set;

public class APIClassLoaderServiceImpl {

  private APIClassLoader instance;

  public APIClassLoaderServiceImpl() {
    this.instance = new APIClassLoader();
  }

  public APIClassLoader getApiClassLoader() {
    return instance;
  }

  private class APIClassLoader {

    private Set<String> blacklist = new HashSet<String>();

    public void triggerAddToBlackList(String name) {
      addToBlackList(name);
    }

    private synchronized void addToBlackList(String name) {
      blacklist.add(name);
    }

    public Set<String> getBlacklist() {
      return blacklist;
    }
  }
}
