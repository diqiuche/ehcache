/*
 * All content copyright Terracotta, Inc., unless otherwise indicated. All rights reserved. Licensed under the Apache
 * License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain
 * a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in
 * writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
 * OF ANY KIND, either express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.terracotta.modules.ehcache;

import org.terracotta.toolkit.Toolkit;
import org.terracotta.toolkit.ToolkitFactory;
import org.terracotta.toolkit.ToolkitInstantiationException;

import java.util.Collections;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

public class TerracottaToolkitBuilder {

  private static final String      TC_TUNNELLED_MBEAN_DOMAINS_KEY = "tunnelledMBeanDomains";
  private static final String      TC_CONFIG_SNIPPET_KEY          = "tcConfigSnippet";
  private static final String      REJOIN_KEY                     = "rejoin";
  private static final String      PRODUCT_ID_KEY                 = "productId";
  private static final String      CLASSLOADER_KEY                = "classloader";

  private boolean                  rejoin;
  private final TCConfigTypeStatus tcConfigTypeStatus             = new TCConfigTypeStatus();
  private final Set<String>        tunnelledMBeanDomains          = Collections.synchronizedSet(new HashSet<String>());
  private String                   productId;
  private ClassLoader              classLoader;

  public Toolkit buildToolkit() throws IllegalStateException {
    if (tcConfigTypeStatus.getState() == TCConfigTypeState.INIT) {
      //
      throw new IllegalStateException(
                                      "Please set the tcConfigSnippet or tcConfigUrl before attempting to create client");
    }
    final String tcConfigOrUrl;
    final boolean isUrl;
    switch (tcConfigTypeStatus.getState()) {
      case TC_CONFIG_SNIPPET:
        tcConfigOrUrl = tcConfigTypeStatus.getTcConfigSnippet();
        isUrl = false;
        break;
      case TC_CONFIG_URL:
        tcConfigOrUrl = tcConfigTypeStatus.getTcConfigUrl();
        isUrl = true;
        break;
      default:
        throw new IllegalStateException("Unknown tc config type - " + tcConfigTypeStatus.getState());
    }
    String toolkitUrl = createTerracottaToolkitUrl(isUrl, tcConfigOrUrl);
    return createToolkit(toolkitUrl, getTerracottaToolkitProperties(isUrl, tcConfigOrUrl, classLoader));
  }

  private Toolkit createToolkit(String url, Properties props) {
    try {
      return ToolkitFactory.createToolkit(url, props);
    } catch (ToolkitInstantiationException e) {
      throw new RuntimeException(e);
    }
  }

  private Properties getTerracottaToolkitProperties(boolean isUrl, String tcConfigOrUrl, ClassLoader loader) {
    Properties properties = new Properties();

    if (loader != null) {
      properties.put(CLASSLOADER_KEY, loader);
    }

    properties.setProperty(TC_TUNNELLED_MBEAN_DOMAINS_KEY, getTunnelledDomainCSV());
    if (!isUrl) {
      properties.setProperty(TC_CONFIG_SNIPPET_KEY, tcConfigOrUrl);
    }
    properties.setProperty(REJOIN_KEY, String.valueOf(rejoin));
    if (productId != null) {
      properties.setProperty(PRODUCT_ID_KEY, productId);
    }
    return properties;
  }

  private String getTunnelledDomainCSV() {
    StringBuilder sb = new StringBuilder();
    for (String domain : tunnelledMBeanDomains) {
      sb.append(domain).append(",");
    }
    // remove last comma
    return sb.deleteCharAt(sb.length() - 1).toString();
  }

  private String createTerracottaToolkitUrl(boolean isUrl, String tcConfigOrUrl) {
    if (isUrl) {
      return "toolkit:nonstop-terracotta://" + tcConfigOrUrl;
    } else {
      return "toolkit:nonstop-terracotta:";
    }
  }

  public TerracottaToolkitBuilder addTunnelledMBeanDomain(String tunnelledMBeanDomain) {
    this.tunnelledMBeanDomains.add(tunnelledMBeanDomain);
    return this;
  }

  public Set<String> getTunnelledMBeanDomains() {
    return Collections.unmodifiableSet(tunnelledMBeanDomains);
  }

  public TerracottaToolkitBuilder removeTunnelledMBeanDomain(String tunnelledMBeanDomain) {
    tunnelledMBeanDomains.remove(tunnelledMBeanDomain);
    return this;
  }

  public TerracottaToolkitBuilder setTCConfigSnippet(String tcConfig) throws IllegalStateException {
    tcConfigTypeStatus.setTcConfigSnippet(tcConfig);
    return this;
  }

  public String getTCConfigSnippet() {
    return tcConfigTypeStatus.getTcConfigSnippet();
  }

  public TerracottaToolkitBuilder setTCConfigUrl(String tcConfigUrl) throws IllegalStateException {
    tcConfigTypeStatus.setTcConfigUrl(tcConfigUrl);
    return this;
  }

  public String getTCConfigUrl() {
    return tcConfigTypeStatus.getTcConfigUrl();
  }

  public boolean isConfigUrl() {
    return tcConfigTypeStatus.getState() == TCConfigTypeState.TC_CONFIG_URL;
  }

  private static enum TCConfigTypeState {
    INIT, TC_CONFIG_SNIPPET, TC_CONFIG_URL;
  }

  private static class TCConfigTypeStatus {
    private TCConfigTypeState state = TCConfigTypeState.INIT;

    private String            tcConfigSnippet;
    private String            tcConfigUrl;

    public synchronized void setTcConfigSnippet(String tcConfigSnippet) {
      if (state == TCConfigTypeState.TC_CONFIG_URL) {
        //
        throw new IllegalStateException("TCConfig url was already set to - " + tcConfigUrl);
      }
      this.state = TCConfigTypeState.TC_CONFIG_SNIPPET;
      this.tcConfigSnippet = tcConfigSnippet;
    }

    public synchronized void setTcConfigUrl(String tcConfigUrl) {
      if (state == TCConfigTypeState.TC_CONFIG_SNIPPET) {
        //
        throw new IllegalStateException("TCConfig snippet was already set to - " + tcConfigSnippet);
      }
      this.state = TCConfigTypeState.TC_CONFIG_URL;
      this.tcConfigUrl = tcConfigUrl;
    }

    public synchronized String getTcConfigSnippet() {
      return tcConfigSnippet;
    }

    public synchronized String getTcConfigUrl() {
      return tcConfigUrl;
    }

    public TCConfigTypeState getState() {
      return state;
    }

  }

  public void setRejoinEnabled(boolean rejoin) {
    this.rejoin = rejoin;
  }

  public String getProductId() {
    return productId;
  }

  public TerracottaToolkitBuilder setClassLoader(ClassLoader loader) {
    this.classLoader = loader;
    return this;
  }

  public TerracottaToolkitBuilder setProductId(final String productId) {
    this.productId = productId;
    return this;
  }
}
