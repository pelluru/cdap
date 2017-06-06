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
package co.cask.cdap.data.stream;

import co.cask.cdap.api.common.Bytes;
import co.cask.cdap.api.data.schema.Schema;
import co.cask.cdap.common.conf.PropertyChangeListener;
import co.cask.cdap.common.conf.PropertyStore;
import co.cask.cdap.common.conf.SyncPropertyUpdater;
import co.cask.cdap.common.io.Codec;
import co.cask.cdap.data2.transaction.stream.StreamConfig;
import co.cask.cdap.internal.io.SchemaTypeAdapter;
import co.cask.cdap.proto.id.StreamId;
import com.google.common.base.Objects;
import com.google.common.util.concurrent.AbstractIdleService;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.twill.common.Cancellable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.Lock;
import javax.annotation.Nullable;

/**
 * Base implementation for {@link StreamCoordinatorClient}.
 */
public abstract class AbstractStreamCoordinatorClient extends AbstractIdleService implements StreamCoordinatorClient {

  private static final Logger LOG = LoggerFactory.getLogger(AbstractStreamCoordinatorClient.class);
  private static final Gson GSON = new GsonBuilder()
    .registerTypeAdapter(Schema.class, new SchemaTypeAdapter()).create();

  private PropertyStore<CoordinatorStreamProperties> propertyStore;

  /**
   * Starts the service.
   *
   * @throws Exception when starting of the service failed
   */
  protected abstract void doStartUp() throws Exception;

  /**
   * Stops the service.
   *
   * @throws Exception when stopping the service could not be performed
   */
  protected abstract void doShutDown() throws Exception;

  /**
   * Creates a {@link PropertyStore}.
   *
   * @param codec Codec for the property stored in the property store
   * @param <T> Type of the property
   * @return A new {@link PropertyStore}.
   */
  protected abstract <T> PropertyStore<T> createPropertyStore(Codec<T> codec);

  /**
   * Returns a {@link Lock} for performing exclusive operation for the given stream.
   */
  protected abstract Lock getLock(StreamId streamId);

  /**
   * Gets invoked when a stream of the given name is created.
   */
  protected abstract void streamCreated(StreamId streamId);

  /**
   * Gets invoked when a stream is deleted.
   */
  protected abstract void streamDeleted(StreamId streamId);

  @Override
  public StreamConfig createStream(StreamId streamId, Callable<StreamConfig> action) throws Exception {
    Lock lock = getLock(streamId);
    lock.lock();
    try {
      StreamConfig config = action.call();
      if (config != null) {
        streamCreated(streamId);
      }
      return config;
    } finally {
      lock.unlock();
    }
  }

  @Override
  public void updateProperties(StreamId streamId, Callable<CoordinatorStreamProperties> action) throws Exception {
    Lock lock = getLock(streamId);
    lock.lock();
    try {
      updateProperties(streamId, action.call()).get();
    } finally {
      lock.unlock();
    }
  }

  @Override
  public void deleteStream(StreamId streamId, Runnable action) throws Exception {
    Lock lock = getLock(streamId);
    lock.lock();
    try {
      action.run();
      // TODO: CDAP-2161 Ideally would be deleting the property. However it is not supported by PropertyStore right now.
      propertyStore.set(streamId.toString(), null).get();
      streamDeleted(streamId);
    } finally {
      lock.unlock();
    }
  }

  @Override
  public <T> T exclusiveAction(StreamId streamId, Callable<T> action) throws Exception {
    Lock lock = getLock(streamId);
    lock.lock();
    try {
      return action.call();
    } finally {
      lock.unlock();
    }
  }

  @Override
  public Cancellable addListener(StreamId streamId, StreamPropertyListener listener) {
    return propertyStore.addChangeListener(streamId.toString(), new StreamPropertyChangeListener(listener));
  }

  @Override
  protected final void startUp() throws Exception {
    propertyStore = createPropertyStore(new Codec<CoordinatorStreamProperties>() {
      @Override
      public byte[] encode(CoordinatorStreamProperties properties) throws IOException {
        return Bytes.toBytes(GSON.toJson(properties));
      }

      @Override
      public CoordinatorStreamProperties decode(byte[] data) throws IOException {
        return GSON.fromJson(Bytes.toString(data), CoordinatorStreamProperties.class);
      }
    });

    try {
      doStartUp();
    } catch (Exception e) {
      propertyStore.close();
      throw e;
    }
  }

  @Override
  protected final void shutDown() throws Exception {
    propertyStore.close();
    doShutDown();
  }

  /**
   * Returns first if first is not {@code null}, otherwise return second.
   * It is different than Guava {@link Objects#firstNonNull(Object, Object)} in the way that it allows the second
   * parameter to be null.
   */
  @Nullable
  private <T> T firstNotNull(@Nullable T first, @Nullable T second) {
    return first != null ? first : second;
  }

  /**
   * Updates stream properties in the property store.
   */
  private ListenableFuture<CoordinatorStreamProperties> updateProperties(StreamId streamId,
                                                                         final CoordinatorStreamProperties properties) {
    return propertyStore.update(streamId.toString(), new SyncPropertyUpdater<CoordinatorStreamProperties>() {

      @Override
      protected CoordinatorStreamProperties compute(@Nullable CoordinatorStreamProperties oldProperties) {
        if (oldProperties == null) {
          return properties;
        }
        // Merge the old and new properties.
        return new CoordinatorStreamProperties(
          firstNotNull(properties.getTTL(), oldProperties.getTTL()),
          firstNotNull(properties.getFormat(), oldProperties.getFormat()),
          firstNotNull(properties.getNotificationThresholdMB(), oldProperties.getNotificationThresholdMB()),
          firstNotNull(properties.getGeneration(), oldProperties.getGeneration()),
          firstNotNull(properties.getDescription(), oldProperties.getDescription()),
          firstNotNull(properties.getOwnerPrincipal(), oldProperties.getOwnerPrincipal()));
      }
    });
  }

  /**
   * A {@link PropertyChangeListener} that convert onChange callback into {@link StreamPropertyListener}.
   */
  private final class StreamPropertyChangeListener extends StreamPropertyListener
    implements PropertyChangeListener<CoordinatorStreamProperties> {

    private final StreamPropertyListener listener;
    private CoordinatorStreamProperties oldProperties;

    private StreamPropertyChangeListener(StreamPropertyListener listener) {
      this.listener = listener;
    }

    @Override
    public void onChange(String name, CoordinatorStreamProperties properties) {
      StreamId streamId = StreamId.fromString(name);
      if (properties == null) {
        deleted(streamId);
        oldProperties = null;
        return;
      }

      Integer generation = properties.getGeneration();
      Integer oldGeneration = (oldProperties == null) ? null : oldProperties.getGeneration();
      if (generation != null && (oldGeneration == null || generation > oldGeneration)) {
        generationChanged(streamId, generation);
      }

      Long ttl = properties.getTTL();
      Long oldTTL = (oldProperties == null) ? null : oldProperties.getTTL();
      if (ttl != null && !ttl.equals(oldTTL)) {
        ttlChanged(streamId, ttl);
      }

      Integer threshold = properties.getNotificationThresholdMB();
      Integer oldThreshold = (oldProperties == null) ? null : oldProperties.getNotificationThresholdMB();

      if (threshold != null && !threshold.equals(oldThreshold)) {
        thresholdChanged(streamId, threshold);
      }
      oldProperties = properties;
    }

    @Override
    public void onError(String name, Throwable failureCause) {
      LOG.error("Exception on PropertyChangeListener for stream {}", name, failureCause);
    }

    @Override
    public void generationChanged(StreamId streamId, int generation) {
      try {
        listener.generationChanged(streamId, generation);
      } catch (Throwable t) {
        LOG.error("Exception while calling StreamPropertyListener.generationChanged", t);
      }
    }

    @Override
    public void ttlChanged(StreamId streamId, long ttl) {
      try {
        listener.ttlChanged(streamId, ttl);
      } catch (Throwable t) {
        LOG.error("Exception while calling StreamPropertyListener.ttlChanged", t);
      }
    }

    @Override
    public void thresholdChanged(StreamId streamId, int threshold) {
      try {
        listener.thresholdChanged(streamId, threshold);
      } catch (Throwable t) {
        LOG.error("Exception while calling StreamPropertyListener.thresholdChanged", t);
      }
    }

    @Override
    public void deleted(StreamId streamId) {
      try {
        listener.deleted(streamId);
      } catch (Throwable t) {
        LOG.error("Exception while calling StreamPropertyListener.deleted", t);
      }
    }
  }
}
