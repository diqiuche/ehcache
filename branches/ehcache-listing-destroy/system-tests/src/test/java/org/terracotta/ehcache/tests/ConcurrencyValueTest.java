/*
 * All content copyright (c) 2003-2008 Terracotta, Inc., except as may otherwise be noted in a separate copyright
 * notice. All rights reserved.
 */
package org.terracotta.ehcache.tests;

import com.tc.test.config.model.TestConfig;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

public class ConcurrencyValueTest extends AbstractCacheTestBase {
  private static final int CDM_DEFAULT_CONCURRENCY = 256;

  public ConcurrencyValueTest(TestConfig testConfig) {
    super("basic-cache-test.xml", testConfig, Client1.class);
  }

  @Override
  protected void evaluateClientOutput(String clientName, int exitCode, File clientOutput) throws Throwable {
    super.evaluateClientOutput(clientName, exitCode, clientOutput);

    FileReader fr = null;
    boolean currencyValueLogged1 = false;
    boolean currencyValueLogged2 = false;
    String currencyValueLogMsg1 = getConcurrencyValueLogMsg("defaultConcurrencyCache", CDM_DEFAULT_CONCURRENCY);
    String currencyValueLogMsg2 = getConcurrencyValueLogMsg("configuredConcurrencyCache", 123);
    try {
      fr = new FileReader(clientOutput);
      BufferedReader reader = new BufferedReader(fr);
      String st = "";
      while ((st = reader.readLine()) != null) {
        if (st.contains(currencyValueLogMsg1)) currencyValueLogged1 = true;
        if (st.contains(currencyValueLogMsg2)) currencyValueLogged2 = true;
      }
    } catch (Exception e) {
      throw new AssertionError(e);
    } finally {
      try {
        fr.close();
      } catch (Exception e) {
        //
      }
    }

    if (!currencyValueLogged1) { throw new AssertionError(); }

    if (!currencyValueLogged2) { throw new AssertionError(); }
  }

  private static String getConcurrencyValueLogMsg(String name, int concurrency) {
    return "Cache [" + name + "] using concurrency: " + concurrency;
  }

}
