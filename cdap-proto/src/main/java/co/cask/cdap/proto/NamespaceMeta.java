/*
 * Copyright © 2014-2017 Cask Data, Inc.
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

package co.cask.cdap.proto;

import co.cask.cdap.proto.id.NamespaceId;

import java.util.Objects;

/**
 * Represents metadata for namespaces
 */
public final class NamespaceMeta {

  public static final NamespaceMeta DEFAULT =
    new NamespaceMeta.Builder().setName(Id.Namespace.DEFAULT).setDescription("Default Namespace").build();

  private final String name;
  private final String description;
  private final NamespaceConfig config;

  private NamespaceMeta(String name, String description, NamespaceConfig config) {
    this.name = name;
    this.description = description;
    this.config = config;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public NamespaceConfig getConfig() {
    return config;
  }

  /**
   * Builder used to build {@link NamespaceMeta}
   */
  public static final class Builder {
    private String name;
    private String description;
    private String schedulerQueueName;
    private String rootDirectory;
    private String hbaseNamespace;
    private String hiveDatabase;
    private String principal;
    private String groupName;
    private String keytabURI;
    private boolean exploreAsPrincipal = true;

    public Builder() {
      // No-Op
    }

    public Builder(NamespaceMeta meta) {
      this.name = meta.getName();
      this.description = meta.getDescription();
      NamespaceConfig config = meta.getConfig();
      if (config != null) {
        this.schedulerQueueName = config.getSchedulerQueueName();
        this.rootDirectory = config.getRootDirectory();
        this.hbaseNamespace = config.getHbaseNamespace();
        this.hiveDatabase = config.getHiveDatabase();
        this.principal = config.getPrincipal();
        this.groupName = config.getGroupName();
        this.keytabURI = config.getKeytabURI();
        this.exploreAsPrincipal = config.isExploreAsPrincipal();
      }
    }

    public Builder setName(NamespaceId id) {
      this.name = id.getNamespace();
      return this;
    }

    public Builder setName(Id.Namespace id) {
      this.name = id.getId();
      return this;
    }

    public Builder setName(String name) {
      this.name = name;
      return this;
    }

    public Builder setDescription(String description) {
      this.description = description;
      return this;
    }

    public Builder setSchedulerQueueName(String schedulerQueueName) {
      this.schedulerQueueName = schedulerQueueName;
      return this;
    }

    public Builder setRootDirectory(final String hdfsDirectory) {
      this.rootDirectory = hdfsDirectory;
      return this;
    }

    public Builder setHBaseNamespace(final String hbaseNamespace) {
      this.hbaseNamespace = hbaseNamespace;
      return this;
    }

    public Builder setHiveDatabase(final String hiveDatabase) {
      this.hiveDatabase = hiveDatabase;
      return this;
    }

    public Builder setPrincipal(String principal) {
      this.principal = principal;
      return this;
    }

    public Builder setGroupName(String groupName) {
      this.groupName = groupName;
      return this;
    }

    public Builder setKeytabURI(String keytabURI) {
      this.keytabURI = keytabURI;
      return this;
    }

    public Builder setExploreAsPrincipal(boolean exploreAsPrincipal) {
      this.exploreAsPrincipal = exploreAsPrincipal;
      return this;
    }

    public NamespaceMeta build() {
      if (name == null) {
        throw new IllegalArgumentException("Namespace id cannot be null.");
      }
      if (description == null) {
        description = "";
      }

      // scheduler queue name is kept non nullable unlike others like root directory, hbase namespace etc for backward
      // compatibility
      if (schedulerQueueName == null) {
        schedulerQueueName = "";
      }

      return new NamespaceMeta(name, description, new NamespaceConfig(schedulerQueueName, rootDirectory,
                                                                      hbaseNamespace, hiveDatabase,
                                                                      principal, groupName, keytabURI,
                                                                      exploreAsPrincipal));
    }
  }

  public NamespaceId getNamespaceId() {
    return new NamespaceId(name);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    NamespaceMeta other = (NamespaceMeta) o;
    return Objects.equals(name, other.name)
      && Objects.equals(description, other.description)
      && Objects.equals(config, other.config);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, description, config);
  }

  @Override
  public String toString() {
    return "NamespaceMeta{" +
      "name='" + name + '\'' +
      ", description='" + description + '\'' +
      ", config=" + getConfig() +
      '}';
  }
}
