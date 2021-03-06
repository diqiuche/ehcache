/*
 * All content copyright Terracotta, Inc., unless otherwise indicated. All rights reserved.
 */
package org.terracotta.modules.ehcache.store;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.config.TerracottaConfiguration;
import net.sf.ehcache.config.TerracottaConfiguration.Consistency;

import org.terracotta.ehcache.tests.AbstractCacheTestBase;
import org.terracotta.ehcache.tests.ClientBase;
import org.terracotta.toolkit.Toolkit;

import com.tc.test.config.model.TestConfig;

import junit.framework.Assert;

public class NoCacheWithMaxBytesLocalDiskTest extends AbstractCacheTestBase {

  public NoCacheWithMaxBytesLocalDiskTest(TestConfig testConfig) {
    super(testConfig, App.class);
  }

  public static class App extends ClientBase {

    public App(String[] args) {
      super(args);
    }

    public static void main(String[] args) {
      new App(args).run();
    }

    @Override
    protected void runTest(Cache cache, Toolkit clusteringToolkit) throws Throwable {
      try {
        createCache("dcv2EventualWithStats", cacheManager, Consistency.EVENTUAL);
        Assert.fail("was able to create a clustered cache with \"maxBytesLocalDisk\" set");
      } catch (Exception e) {
        // expected exception
      }
    }

    private Cache createCache(String cacheName, CacheManager cm, Consistency consistency) {
      System.out.println("creating " + cacheName);
      CacheConfiguration cacheConfiguration = new CacheConfiguration();
      cacheConfiguration.setName(cacheName);
      cacheConfiguration.setMaxBytesLocalDisk(1024 * 1024L);
      cacheConfiguration.setEternal(false);
      cacheConfiguration.setMaxBytesLocalHeap(10 * 1024 * 1024L);
      cacheConfiguration.setOverflowToOffHeap(false);

      TerracottaConfiguration tcConfiguration = new TerracottaConfiguration();
      tcConfiguration.setConsistency(consistency);
      cacheConfiguration.addTerracotta(tcConfiguration);

      Cache cache = new Cache(cacheConfiguration);
      cm.addCache(cache);
      return cache;
    }
  }

}
