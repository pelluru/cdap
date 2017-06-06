/*
 * Copyright © 2014 Cask Data, Inc.
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

package co.cask.cdap.client;

import co.cask.cdap.api.annotation.Beta;
import co.cask.cdap.client.config.ClientConfig;
import co.cask.cdap.client.util.RESTClient;
import co.cask.cdap.common.DatasetTypeNotFoundException;
import co.cask.cdap.common.UnauthenticatedException;
import co.cask.cdap.common.utils.Tasks;
import co.cask.cdap.proto.DatasetTypeMeta;
import co.cask.cdap.proto.Id;
import co.cask.cdap.proto.id.DatasetTypeId;
import co.cask.cdap.proto.id.NamespaceId;
import co.cask.cdap.security.spi.authorization.UnauthorizedException;
import co.cask.common.http.HttpMethod;
import co.cask.common.http.HttpResponse;
import co.cask.common.http.ObjectResponse;
import com.google.common.base.Throwables;
import com.google.common.reflect.TypeToken;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.inject.Inject;

/**
 * Provides ways to interact with CDAP Dataset types.
 */
@Beta
public class DatasetTypeClient {

  private final RESTClient restClient;
  private final ClientConfig config;

  @Inject
  public DatasetTypeClient(ClientConfig config, RESTClient restClient) {
    this.config = config;
    this.restClient = restClient;
  }

  public DatasetTypeClient(ClientConfig config) {
    this(config, new RESTClient(config));
  }

  /**
   * Lists all dataset types.
   *
   * @return list of {@link DatasetTypeMeta}s.
   * @throws IOException if a network error occurred
   * @throws UnauthenticatedException if the request is not authorized successfully in the gateway server
   * @deprecated since 4.0.0. Use {@link #list(NamespaceId)} instead.
   */
  @Deprecated
  public List<DatasetTypeMeta> list(Id.Namespace namespace)
    throws IOException, UnauthenticatedException, UnauthorizedException {
    return list(namespace.toEntityId());
  }

  /**
   * Lists all dataset types.
   *
   * @return list of {@link DatasetTypeMeta}s.
   * @throws IOException if a network error occurred
   * @throws UnauthenticatedException if the request is not authorized successfully in the gateway server
   */
  public List<DatasetTypeMeta> list(NamespaceId namespace)
    throws IOException, UnauthenticatedException, UnauthorizedException {
    URL url = config.resolveNamespacedURLV3(namespace, "data/types");
    HttpResponse response = restClient.execute(HttpMethod.GET, url, config.getAccessToken());
    return ObjectResponse.fromJsonBody(response, new TypeToken<List<DatasetTypeMeta>>() { }).getResponseObject();
  }

  /**
   * Gets information about a dataset type.
   *
   * @param type the dataset type
   * @return {@link DatasetTypeMeta} of the dataset type
   * @throws DatasetTypeNotFoundException if the dataset type could not be found
   * @throws IOException if a network error occurred
   * @throws UnauthenticatedException if the request is not authorized successfully in the gateway server
   * @deprecated since 4.0.0. Use {@link #get(DatasetTypeId)} instead.
   */
  @Deprecated
  public DatasetTypeMeta get(Id.DatasetType type)
    throws DatasetTypeNotFoundException, IOException, UnauthenticatedException, UnauthorizedException {
    return get(type.toEntityId());
  }
  /**
   * Gets information about a dataset type.
   *
   * @param type the dataset type
   * @return {@link DatasetTypeMeta} of the dataset type
   * @throws DatasetTypeNotFoundException if the dataset type could not be found
   * @throws IOException if a network error occurred
   * @throws UnauthenticatedException if the request is not authorized successfully in the gateway server
   */
  public DatasetTypeMeta get(DatasetTypeId type)
    throws DatasetTypeNotFoundException, IOException, UnauthenticatedException, UnauthorizedException {

    URL url = config.resolveNamespacedURLV3(type.getParent(), String.format("data/types/%s", type.getType()));
    HttpResponse response = restClient.execute(HttpMethod.GET, url, config.getAccessToken(),
                                               HttpURLConnection.HTTP_NOT_FOUND);
    if (response.getResponseCode() == HttpURLConnection.HTTP_NOT_FOUND) {
      throw new DatasetTypeNotFoundException(type);
    }

    return ObjectResponse.fromJsonBody(response, DatasetTypeMeta.class).getResponseObject();
  }

  /**
   * Checks if a dataset type exists.
   *
   * @param type the dataset type to check
   * @return true if the dataset type exists
   * @throws IOException if a network error occurred
   * @throws UnauthenticatedException if the request is not authorized successfully in the gateway server
   * @deprecated since 4.0.0. Use {@link #exists(DatasetTypeId)} instead.
   */
  @Deprecated
  public boolean exists(Id.DatasetType type)
    throws DatasetTypeNotFoundException, IOException, UnauthenticatedException, UnauthorizedException {
    return exists(type.toEntityId());
  }

  /**
   * Checks if a dataset type exists.
   *
   * @param type the dataset type to check
   * @return true if the dataset type exists
   * @throws IOException if a network error occurred
   * @throws UnauthenticatedException if the request is not authorized successfully in the gateway server
   */
  public boolean exists(DatasetTypeId type)
    throws DatasetTypeNotFoundException, IOException, UnauthenticatedException, UnauthorizedException {
    URL url = config.resolveNamespacedURLV3(type.getParent(), String.format("data/types/%s", type.getType()));
    HttpResponse response = restClient.execute(HttpMethod.GET, url, config.getAccessToken(),
                                               HttpURLConnection.HTTP_NOT_FOUND);
    return response.getResponseCode() != HttpURLConnection.HTTP_NOT_FOUND;
  }

  /**
   * Waits for a dataset type to exist.
   *
   * @param type the dataset type to check
   * @param timeout time to wait before timing out
   * @param timeoutUnit time unit of timeout
   * @throws IOException if a network error occurred
   * @throws UnauthenticatedException if the request is not authorized successfully in the gateway server
   * @throws TimeoutException if the dataset type was not yet existent before {@code timeout} milliseconds
   * @throws InterruptedException if interrupted while waiting
   * @deprecated since 4.0.0.
   */
  @Deprecated
  public void waitForExists(final Id.DatasetType type, long timeout, TimeUnit timeoutUnit)
    throws IOException, UnauthenticatedException, TimeoutException, InterruptedException {
    try {
      Tasks.waitFor(true, new Callable<Boolean>() {
        @Override
        public Boolean call() throws Exception {
          return exists(type.toEntityId());
        }
      }, timeout, timeoutUnit, 1, TimeUnit.SECONDS);
    } catch (ExecutionException e) {
      Throwables.propagateIfPossible(e.getCause(), IOException.class, UnauthenticatedException.class);
    }
  }

  /**
   * Waits for a dataset type to be deleted.
   *
   * @param type the dataset type to check
   * @param timeout time to wait before timing out
   * @param timeoutUnit time unit of timeout
   * @throws IOException if a network error occurred
   * @throws UnauthenticatedException if the request is not authorized successfully in the gateway server
   * @throws TimeoutException if the dataset type was not yet deleted before {@code timeout} milliseconds
   * @throws InterruptedException if interrupted while waiting
   * @deprecated since 4.0.0.
   */
  @Deprecated
  public void waitForDeleted(final Id.DatasetType type, long timeout, TimeUnit timeoutUnit)
    throws IOException, UnauthenticatedException, TimeoutException, InterruptedException {
    try {
      Tasks.waitFor(false, new Callable<Boolean>() {
        @Override
        public Boolean call() throws Exception {
          return exists(type.toEntityId());
        }
      }, timeout, timeoutUnit, 1, TimeUnit.SECONDS);
    } catch (ExecutionException e) {
      Throwables.propagateIfPossible(e.getCause(), IOException.class, UnauthenticatedException.class);
    }
  }

}
