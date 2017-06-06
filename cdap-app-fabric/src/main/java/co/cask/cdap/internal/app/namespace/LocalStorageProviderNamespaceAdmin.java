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

package co.cask.cdap.internal.app.namespace;

import co.cask.cdap.common.conf.CConfiguration;
import co.cask.cdap.common.namespace.NamespaceQueryAdmin;
import co.cask.cdap.common.namespace.NamespacedLocationFactory;
import co.cask.cdap.explore.client.ExploreFacade;
import com.google.inject.Inject;

/**
 * Manages namespaces on local underlying systems.
 */
public final class LocalStorageProviderNamespaceAdmin extends AbstractStorageProviderNamespaceAdmin {

  @Inject
  LocalStorageProviderNamespaceAdmin(CConfiguration cConf, NamespacedLocationFactory namespacedLocationFactory,
                                     ExploreFacade exploreFacade, NamespaceQueryAdmin namespaceQueryAdmin) {
    super(cConf, namespacedLocationFactory, exploreFacade, namespaceQueryAdmin);
  }
}
