/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.whirr.karaf.itest;

import java.io.IOException;

import java.util.Properties;

import org.apache.felix.service.command.CommandProcessor;
import org.apache.felix.service.command.CommandSession;
import org.ops4j.pax.exam.MavenUtils;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.TestProbeBuilder;
import org.ops4j.pax.exam.junit.ProbeBuilder;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.openengsb.labs.paxexam.karaf.options.KarafDistributionOption.karafDistributionConfiguration;
import static org.ops4j.pax.exam.CoreOptions.maven;

public class WhirrKarafTestSupport {

  static final Long COMMAND_TIMEOUT = 300000L;
  static final Long SERVICE_TIMEOUT = 30000L;
  static final Long DEFAULT_WAIT = 20000L;

  private static final String UNINSTALLED = "[uninstalled]";
  private static final String INSTALLED = "[installed  ]";

  static final String GROUP_ID = "org.apache.karaf";
  static final String ARTIFACT_ID = "apache-karaf";

  static final String WHIRR_VERSION = getWhirrVersion();

  static final String WHIRR_FEATURE_URL = String.format("mvn:org.apache.whirr.karaf/apache-whirr/%s/xml/features", WHIRR_VERSION);

  static final String DEBUG_OPTS = " --java-opts \"-Xdebug -Xnoagent -Djava.compiler=NONE -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=%s\"";

  ExecutorService executor = Executors.newCachedThreadPool();

  @Inject
  protected BundleContext bundleContext;

  /**
   * @param probe
   * @return
   */
  @ProbeBuilder
  public TestProbeBuilder probeConfiguration(TestProbeBuilder probe) {
    probe.setHeader(Constants.DYNAMICIMPORT_PACKAGE, "*,org.apache.felix.service.*;status=provisional");

    return probe;
  }

  /**
   * Installs the Whiir feature
   */
  protected void installWhirr() {
    System.err.println(executeCommand("features:addurl " + WHIRR_FEATURE_URL));
    System.err.println(executeCommand("features:listurl"));
    System.err.println(executeCommand("features:list"));
    executeCommand("features:install whirr");
  }

  protected void unInstallWhirr() {
    System.err.println(executeCommand("features:uninstall whirr"));
  }

  /**
   * Create an {@link org.ops4j.pax.exam.Option} for using a .
   *
   * @return
   */
  protected Option whirrDistributionConfiguration() {
    return karafDistributionConfiguration().frameworkUrl(
      maven().groupId(GROUP_ID).artifactId(ARTIFACT_ID).versionAsInProject().type("tar.gz"))
      .karafVersion(MavenUtils.getArtifactVersion(GROUP_ID, ARTIFACT_ID)).name("Apache Karaf").unpackDirectory(new File("target/paxexam/"));
  }

  /**
   * Executes a shell command and returns output as a String.
   * Commands have a default timeout of 10 seconds.
   *
   * @param command
   * @return
   */
  protected String executeCommand(final String command) {
    return executeCommand(command, COMMAND_TIMEOUT, false);
  }

  /**
   * Executes a shell command and returns output as a String.
   * Commands have a default timeout of 10 seconds.
   *
   * @param command The command to execute.
   * @param timeout The amount of time in millis to wait for the command to execute.
   * @param silent  Specifies if the command should be displayed in the screen.
   * @return
   */
  protected String executeCommand(final String command, final Long timeout, final Boolean silent) {
    String response;
    final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    final PrintStream printStream = new PrintStream(byteArrayOutputStream);
    final CommandProcessor commandProcessor = getOsgiService(CommandProcessor.class);
    final CommandSession commandSession = commandProcessor.createSession(System.in, printStream, System.err);
    FutureTask<String> commandFuture = new FutureTask<String>(
      new Callable<String>() {
        public String call() {
          try {
            if (!silent) {
              System.err.println(command);
            }
            commandSession.execute(command);
          } catch (Exception e) {
            e.printStackTrace(System.err);
          }
          printStream.flush();
          return byteArrayOutputStream.toString();
        }
      });

    try {
      executor.submit(commandFuture);
      response = commandFuture.get(timeout, TimeUnit.MILLISECONDS);
    } catch (Exception e) {
      e.printStackTrace(System.err);
      response = "SHELL COMMAND TIMED OUT: ";
    }

    return response;
  }

  /**
   * Executes multiple commands inside a Single Session.
   * Commands have a default timeout of 10 seconds.
   *
   * @param commands
   * @return
   */
  protected String executeCommands(final String... commands) {
    String response;
    final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    final PrintStream printStream = new PrintStream(byteArrayOutputStream);
    final CommandProcessor commandProcessor = getOsgiService(CommandProcessor.class);
    final CommandSession commandSession = commandProcessor.createSession(System.in, printStream, System.err);
    FutureTask<String> commandFuture = new FutureTask<String>(
      new Callable<String>() {
        public String call() {
          try {
            for (String command : commands) {
              System.err.println(command);
              commandSession.execute(command);
            }
          } catch (Exception e) {
            e.printStackTrace(System.err);
          }
          return byteArrayOutputStream.toString();
        }
      });

    try {
      executor.submit(commandFuture);
      response = commandFuture.get(COMMAND_TIMEOUT, TimeUnit.MILLISECONDS);
    } catch (Exception e) {
      e.printStackTrace(System.err);
      response = "SHELL COMMAND TIMED OUT: ";
    }

    return response;
  }

  protected Bundle getInstalledBundle(String symbolicName) {
    for (Bundle b : bundleContext.getBundles()) {
      if (b.getSymbolicName().equals(symbolicName)) {
        return b;
      }
    }
    for (Bundle b : bundleContext.getBundles()) {
      System.err.println("Bundle: " + b.getSymbolicName());
    }
    throw new RuntimeException("Bundle " + symbolicName + " does not exist");
  }

  /*
  * Explode the dictionary into a ,-delimited list of key=value pairs
  */
  private static String explode(Dictionary dictionary) {
    Enumeration keys = dictionary.keys();
    StringBuffer result = new StringBuffer();
    while (keys.hasMoreElements()) {
      Object key = keys.nextElement();
      result.append(String.format("%s=%s", key, dictionary.get(key)));
      if (keys.hasMoreElements()) {
        result.append(", ");
      }
    }
    return result.toString();
  }

  protected <T> T getOsgiService(Class<T> type, long timeout) {
    return getOsgiService(type, null, timeout);
  }

  protected <T> T getOsgiService(Class<T> type) {
    return getOsgiService(type, null, SERVICE_TIMEOUT);
  }

  protected <T> T getOsgiService(Class<T> type, String filter, long timeout) {
    ServiceTracker tracker = null;
    try {
      String flt;
      if (filter != null) {
        if (filter.startsWith("(")) {
          flt = "(&(" + Constants.OBJECTCLASS + "=" + type.getName() + ")" + filter + ")";
        } else {
          flt = "(&(" + Constants.OBJECTCLASS + "=" + type.getName() + ")(" + filter + "))";
        }
      } else {
        flt = "(" + Constants.OBJECTCLASS + "=" + type.getName() + ")";
      }
      Filter osgiFilter = FrameworkUtil.createFilter(flt);
      tracker = new ServiceTracker(bundleContext, osgiFilter, null);
      tracker.open(true);
      // Note that the tracker is not closed to keep the reference
      // This is buggy, as the service reference may change i think
      Object svc = type.cast(tracker.waitForService(timeout));
      if (svc == null) {
        Dictionary dic = bundleContext.getBundle().getHeaders();
        System.err.println("Test bundle headers: " + explode(dic));

        for (ServiceReference ref : asCollection(bundleContext.getAllServiceReferences(null, null))) {
          System.err.println("ServiceReference: " + ref);
        }

        for (ServiceReference ref : asCollection(bundleContext.getAllServiceReferences(null, flt))) {
          System.err.println("Filtered ServiceReference: " + ref);
        }

        throw new RuntimeException("Gave up waiting for service " + flt);
      }
      return type.cast(svc);
    } catch (InvalidSyntaxException e) {
      throw new IllegalArgumentException("Invalid filter", e);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    } finally {
      if (tracker != null) {
        tracker.close();
      }
    }
  }

  /*
  * Provides an iterable collection of references, even if the original array is null
  */
  private static Collection<ServiceReference> asCollection(ServiceReference[] references) {
    return references != null ? Arrays.asList(references) : Collections.<ServiceReference>emptyList();
  }

  private static String getWhirrVersion() {
    Properties props = new Properties();
    try {
      props.load(WhirrKarafTestSupport.class.getResourceAsStream("/whirr-karaf-itests.properties"));
      return props.getProperty("version");
    } catch (IOException e) {
      throw new RuntimeException("Could not load properties containing whirr version number from classpath:/whirr-karaf-itests.properties");
    }
  }

}
