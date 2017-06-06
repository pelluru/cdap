/*
 * Copyright © 2016 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package co.cask.cdap.internal.app.runtime;

import co.cask.cdap.api.Resources;
import co.cask.cdap.api.common.RuntimeArguments;
import co.cask.cdap.app.runtime.Arguments;
import co.cask.cdap.common.conf.CConfiguration;
import co.cask.cdap.common.conf.Constants;
import co.cask.cdap.common.service.RetryStrategies;
import co.cask.cdap.common.service.RetryStrategy;
import co.cask.cdap.logging.appender.LogAppenderInitializer;
import co.cask.cdap.proto.ProgramType;
import org.apache.tephra.TxConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Utility class to help extract system properties from the program runtime arguments.
 */
public final class SystemArguments {

  private static final Logger LOG = LoggerFactory.getLogger(SystemArguments.class);

  private static final String MEMORY_KEY = "system.resources.memory";
  private static final String CORES_KEY = "system.resources.cores";
  private static final String LOG_LEVEL = "system.log.level";
  private static final String RETRY_POLICY_TYPE = "system." + Constants.Retry.TYPE;
  private static final String RETRY_POLICY_MAX_TIME_SECS = "system." + Constants.Retry.MAX_TIME_SECS;
  private static final String RETRY_POLICY_MAX_RETRIES = "system." + Constants.Retry.MAX_RETRIES;
  private static final String RETRY_POLICY_DELAY_BASE_MS = "system." + Constants.Retry.DELAY_BASE_MS;
  private static final String RETRY_POLICY_DELAY_MAX_MS = "system." + Constants.Retry.DELAY_MAX_MS;
  public static final String TRANSACTION_TIMEOUT = "system.data.tx.timeout";

  public static Map<String, String> getLogLevels(Map<String, String> args) {
    Map<String, String> logLevels = new HashMap<>();
    for (Map.Entry<String, String> entry : args.entrySet()) {
      String loggerName = entry.getKey();
      if (loggerName.length() > LOG_LEVEL.length() && loggerName.startsWith(LOG_LEVEL)) {
        logLevels.put(loggerName.substring(LOG_LEVEL.length() + 1), entry.getValue());
      }
    }
    String logLevel = args.get(LOG_LEVEL);
    if (logLevel != null) {
      logLevels.put(Logger.ROOT_LOGGER_NAME, logLevel);
    }
    return logLevels;
  }

  /**
   * Set the log level for the {@link LogAppenderInitializer}.
   *
   * Same as calling {@link #setLogLevel(Map, LogAppenderInitializer)} with first argument from
   * {@link Arguments#asMap()}
   */
  public static void setLogLevel(Arguments args, LogAppenderInitializer initializer) {
    setLogLevel(args.asMap(), initializer);
  }

  /**
   * Set the log level for the {@link LogAppenderInitializer}.
   * @param args the arguments to use for looking up resources configurations
   * @param initializer the LogAppenderInitializer which will be used to set up the log level
   */
  public static void setLogLevel(Map<String, String> args, LogAppenderInitializer initializer) {
    initializer.setLogLevels(getLogLevels(args));
  }

  /**
   * Set the transaction timeout in the given arguments.
   */
  public static void setTransactionTimeout(Map<String, String> args, int timeout) {
    args.put(TRANSACTION_TIMEOUT, String.valueOf(timeout));
  }

  /**
   * Returns the transction timeout based on the given arguments or, as fallback, the CConfiguration.
   *
   * @returns the integer value of the argument system.data.tx.timeout, or if that is not given in the arguments,
   *          the value for data.tx.timeout from the CConfiguration.
   * @throws IllegalArgumentException if the transaction timeout exceeds the transaction timeout limit given in the
   *         CConfiguratuion.
   */
  public static int getTransactionTimeout(Map<String, String> args, CConfiguration cConf) {
    Integer timeout = getPositiveInt(args, TRANSACTION_TIMEOUT, "transaction timeout");
    if (timeout == null) {
      return cConf.getInt(TxConstants.Manager.CFG_TX_TIMEOUT);
    }
    int maxTimeout = cConf.getInt(TxConstants.Manager.CFG_TX_MAX_TIMEOUT);
    if (timeout > maxTimeout) {
      throw new IllegalArgumentException(String.format(
        "Transaction timeout (%s) of %d seconds must not exceed the transaction timeout limit (%s) of %d",
        TRANSACTION_TIMEOUT, timeout, TxConstants.Manager.CFG_TX_MAX_TIMEOUT, maxTimeout));
    }
    return timeout;
  }

  /**
   * Validates the custom transaction timeout, if specified in the given arguments.
   *
   * @throws IllegalArgumentException if the transaction timeout exceeds the transaction timeout limit given in the
   *         CConfiguration.
   */
  public static void validateTransactionTimeout(Map<String, String> args, CConfiguration cConf) {
    validateTransactionTimeout(args, cConf, null, null);
  }

  /**
   * Validates the custom transaction timeout, if specified in the given arguments. If scope and name are not null,
   * validates only for that given scope (for example, flowlet.myFlowlet).
   *
   * @throws IllegalArgumentException if the transaction timeout exceeds the transaction timeout limit given in the
   *         CConfiguration.
   */
  public static void validateTransactionTimeout(Map<String, String> args, CConfiguration cConf,
                                                @Nullable String scope, @Nullable String name) {
    String argName = TRANSACTION_TIMEOUT;
    if (scope != null && name != null) {
      argName = RuntimeArguments.addScope(scope, name, TRANSACTION_TIMEOUT);
    }
    Integer timeout = getPositiveInt(args, argName, "transaction timeout");
    if (timeout != null) {
      int maxTimeout = cConf.getInt(TxConstants.Manager.CFG_TX_MAX_TIMEOUT);
      if (timeout > maxTimeout) {
        throw new IllegalArgumentException(String.format(
          "Transaction timeout (%s) of %d seconds must not exceed the transaction timeout limit (%s) of %d",
          argName, timeout, TxConstants.Manager.CFG_TX_MAX_TIMEOUT, maxTimeout));
      }
    }
  }

  /**
   * Get the retry strategy for a program given its arguments and the CDAP defaults for the program type.
   *
   * @return the retry strategy to use for internal calls
   * @throws IllegalArgumentException if there is an invalid value for an argument
   */
  public static RetryStrategy getRetryStrategy(Map<String, String> args, ProgramType programType,
                                               CConfiguration cConf) {
    String keyPrefix;
    switch (programType) {
      case MAPREDUCE:
        keyPrefix = Constants.Retry.MAPREDUCE_PREFIX;
        break;
      case SPARK:
        keyPrefix = Constants.Retry.SPARK_PREFIX;
        break;
      case WORKFLOW:
        keyPrefix = Constants.Retry.WORKFLOW_PREFIX;
        break;
      case WORKER:
        keyPrefix = Constants.Retry.WORKER_PREFIX;
        break;
      case SERVICE:
        keyPrefix = Constants.Retry.SERVICE_PREFIX;
        break;
      case FLOW:
        keyPrefix = Constants.Retry.FLOW_PREFIX;
        break;
      case CUSTOM_ACTION:
        keyPrefix = Constants.Retry.CUSTOM_ACTION_PREFIX;
        break;
      default:
        throw new IllegalArgumentException("Invalid program type " + programType);
    }

    // Override from runtime arguments
    CConfiguration policyConf = CConfiguration.copy(cConf);
    String typeStr = args.get(RETRY_POLICY_TYPE);
    if (typeStr != null) {
      policyConf.set(keyPrefix + Constants.Retry.TYPE, typeStr);
    }
    int maxRetries = getNonNegativeInt(args, RETRY_POLICY_MAX_RETRIES, RETRY_POLICY_MAX_RETRIES, -1);
    if (maxRetries >= 0) {
      policyConf.setInt(keyPrefix + Constants.Retry.MAX_RETRIES, maxRetries);
    }
    long maxTimeSecs = getNonNegativeLong(args, RETRY_POLICY_MAX_TIME_SECS, RETRY_POLICY_MAX_TIME_SECS, -1L);
    if (maxTimeSecs >= 0) {
      policyConf.setLong(keyPrefix + Constants.Retry.MAX_TIME_SECS, maxTimeSecs);
    }
    long baseDelay = getNonNegativeLong(args, RETRY_POLICY_DELAY_BASE_MS, RETRY_POLICY_DELAY_BASE_MS, -1L);
    if (baseDelay >= 0) {
      policyConf.setLong(keyPrefix + Constants.Retry.DELAY_BASE_MS, baseDelay);
    }
    long maxDelay = getNonNegativeLong(args, RETRY_POLICY_DELAY_MAX_MS, RETRY_POLICY_DELAY_MAX_MS, -1L);
    if (maxDelay >= 0) {
      policyConf.setLong(keyPrefix + Constants.Retry.DELAY_MAX_MS, maxDelay);
    }

    return RetryStrategies.fromConfiguration(policyConf, keyPrefix);
  }

  /**
   * Returns the {@link Resources} based on configurations in the given arguments.
   *
   * Same as calling {@link #getResources(Map, Resources)} with first argument from {@link Arguments#asMap()}.
   */
  public static Resources getResources(Arguments args, @Nullable Resources defaultResources) {
    return getResources(args.asMap(), defaultResources);
  }

  /**
   * Returns the {@link Resources} based on configurations in the given arguments.
   *
   * @param args the arguments to use for looking up resources configurations
   * @param defaultResources default resources to use if resources configurations are missing from the arguments.
   *                         If it is {@code null}, the default values in {@link Resources} will be used.
   */
  public static Resources getResources(Map<String, String> args, @Nullable Resources defaultResources) {
    Integer memory = getPositiveInt(args, MEMORY_KEY, "memory size");
    Integer cores = getPositiveInt(args, CORES_KEY, "number of cores");
    defaultResources = defaultResources == null ? new Resources() : defaultResources;

    if (memory == null && cores == null) {
      return defaultResources;
    }
    return new Resources(memory != null ? memory : defaultResources.getMemoryMB(),
                         cores != null ? cores : defaultResources.getVirtualCores());
  }

  /**
   * Gets a positive integer value from the given map using the given key.
   * If there is no such key or if the value is not positive, returns {@code null}.
   */
  private static Integer getPositiveInt(Map<String, String> map, String key, String description) {
    Integer val = getInt(map, key, description);
    if (val != null && val <= 0) {
      LOG.warn("Ignoring invalid {} '{}' from runtime arguments. It must be a positive integer.",
               description, val);
      return null;
    }
    return val;
  }

  /**
   * Gets a non-negative (can be 0) integer value from the given map using the given key.
   * If there is no such key or if the value is negative, returns the default.
   */
  private static int getNonNegativeInt(Map<String, String> map, String key, String description, int defaultVal) {
    Integer val = getInt(map, key, description);
    if (val == null) {
      return defaultVal;
    } else if (val < 0) {
      LOG.warn("Ignoring invalid {} '{}' from runtime arguments. It must be a non-negative integer.",
               description, val);
      return defaultVal;
    }
    return val;
  }

  /**
   * Gets a non-negative (can be 0) long value from the given map using the given key.
   * If there is no such key or if the value is negative, returns the default.
   */
  private static long getNonNegativeLong(Map<String, String> map, String key, String description, long defaultVal) {
    Long val = getLong(map, key, description);
    if (val == null) {
      return defaultVal;
    } else if (val < 0) {
      LOG.warn("Ignoring invalid {} '{}' from runtime arguments. It must be a non-negative long.",
               description, val);
      return defaultVal;
    }
    return val;
  }

  private static Integer getInt(Map<String, String> map, String key, String description) {
    String value = map.get(key);
    if (value == null) {
      return null;
    }

    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      // Only the log the stack trace as debug, as usually it's not needed.
      LOG.warn("Ignoring invalid {} '{}' from runtime arguments. It could not be parsed as an integer.",
               description, value);
      LOG.debug("Invalid {}", description, e);
    }

    return null;
  }

  private static Long getLong(Map<String, String> map, String key, String description) {
    String value = map.get(key);
    if (value == null) {
      return null;
    }

    try {
      return Long.parseLong(value);
    } catch (NumberFormatException e) {
      // Only the log the stack trace as debug, as usually it's not needed.
      LOG.warn("Ignoring invalid {} '{}' from runtime arguments. It could not be parsed as a long.",
               description, value);
      LOG.debug("Invalid {}", description, e);
    }

    return null;
  }

  private SystemArguments() {
  }
}
