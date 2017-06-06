/*
 * Copyright © 2015 Cask Data, Inc.
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

package co.cask.cdap.api.plugin;

import co.cask.cdap.api.annotation.Beta;
import co.cask.cdap.api.macro.Macros;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Plugin instance properties.
 */
@Beta
public class PluginProperties implements Serializable {

  private static final long serialVersionUID = -7396484717511717753L;

  // Currently only support String->String map.
  private final Map<String, String> properties;
  private final Macros macros;

  private PluginProperties(Map<String, String> properties, @Nullable Macros macros) {
    this.properties = properties;
    this.macros = macros;
  }

  public static Builder builder() {
    return new Builder();
  }

  private PluginProperties(Map<String, String> properties) {
    this(properties, new Macros());
  }

  public Map<String, String> getProperties() {
    return properties;
  }

  public Macros getMacros() {
    return  (macros == null) ? new Macros() : macros;
  }

  /**
   * Creates and returns a new instance of Plugin properties with current properties and the passed macros parameter.
   * Note this is used internally by the CDAP Platform and it is advisable
   * that plugin developers not use this method.
   * @param macros set of macros used by this plugin.
   * @return new instance of plugin properties with macros set.
   */
  public PluginProperties setMacros(Macros macros) {
    return new PluginProperties(getProperties(), macros);
  }

  /**
   * A builder to create {@link PluginProperties} instance.
   */
  public static final class Builder {

    private final Map<String, String> properties;

    private Builder() {
      this.properties = new HashMap<>();
    }

    /**
     * Adds multiple properties.
     *
     * @param properties map of properties to add.
     * @return this builder
     */
    public Builder addAll(Map<String, String> properties) {
      this.properties.putAll(properties);
      return this;
    }

    /**
     * Adds a property
     * @param key the name of the property
     * @param value the value of the property
     * @return this builder
     */
    public Builder add(String key, String value) {
      this.properties.put(key, value);
      return this;
    }

    /**
     * Creates a new instance of {@link PluginProperties} with the properties added to this builder prior to this call.
     */
    public PluginProperties build() {
      return new PluginProperties(Collections.unmodifiableMap(new HashMap<>(properties)));
    }
  }
}
