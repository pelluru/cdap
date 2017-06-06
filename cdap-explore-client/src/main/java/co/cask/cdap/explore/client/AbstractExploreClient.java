/*
 * Copyright © 2015-2017 Cask Data, Inc.
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

package co.cask.cdap.explore.client;

import co.cask.cdap.api.data.format.FormatSpecification;
import co.cask.cdap.api.dataset.DatasetSpecification;
import co.cask.cdap.api.dataset.lib.PartitionKey;
import co.cask.cdap.common.ServiceUnavailableException;
import co.cask.cdap.common.UnauthenticatedException;
import co.cask.cdap.explore.service.Explore;
import co.cask.cdap.explore.service.ExploreException;
import co.cask.cdap.explore.service.HandleNotFoundException;
import co.cask.cdap.explore.service.MetaDataInfo;
import co.cask.cdap.proto.ColumnDesc;
import co.cask.cdap.proto.NamespaceMeta;
import co.cask.cdap.proto.QueryHandle;
import co.cask.cdap.proto.QueryResult;
import co.cask.cdap.proto.QueryStatus;
import co.cask.cdap.proto.id.DatasetId;
import co.cask.cdap.proto.id.NamespaceId;
import co.cask.cdap.proto.id.StreamId;
import com.google.common.base.Functions;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gson.Gson;
import org.apache.twill.common.Threads;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

/**
 * A base for an Explore Client that talks to a server implementing {@link Explore} over HTTP/HTTPS.
 */
public abstract class AbstractExploreClient extends ExploreHttpClient implements ExploreClient {
  private static final Gson GSON = new Gson();
  private static final Logger LOG = LoggerFactory.getLogger(AbstractExploreClient.class);

  private final ListeningScheduledExecutorService executor;

  protected AbstractExploreClient() {
    executor = MoreExecutors.listeningDecorator(
      Executors.newSingleThreadScheduledExecutor(Threads.createDaemonThreadFactory("explore-client-executor")));
  }

  @Override
  public void close() throws IOException {
    // This will cancel all the running tasks, with interruption - that means that all
    // queries submitted by this executor will be closed
    executor.shutdownNow();
  }

  @Override
  public void ping() throws UnauthenticatedException, ServiceUnavailableException, ExploreException {
    super.ping();
  }

  @Override
  public ListenableFuture<Void> disableExploreDataset(final DatasetId datasetInstance) {
    ListenableFuture<ExploreExecutionResult> futureResults = getResultsFuture(new HandleProducer() {
      @Override
      public QueryHandle getHandle() throws ExploreException, SQLException {
        return doDisableExploreDataset(datasetInstance, null);
      }
    });

    // Exceptions will be thrown in case of an error in the futureHandle
    return Futures.transform(futureResults, Functions.<Void>constant(null));
  }

  @Override
  public ListenableFuture<Void> disableExploreDataset(final DatasetId datasetInstance,
                                                      final DatasetSpecification spec) {
    ListenableFuture<ExploreExecutionResult> futureResults = getResultsFuture(new HandleProducer() {
      @Override
      public QueryHandle getHandle() throws ExploreException, SQLException {
        return doDisableExploreDataset(datasetInstance, spec);
      }
    });

    // Exceptions will be thrown in case of an error in the futureHandle
    return Futures.transform(futureResults, Functions.<Void>constant(null));
  }

  @Override
  public ListenableFuture<Void> updateExploreDataset(final DatasetId datasetInstance,
                                                     final DatasetSpecification oldSpec,
                                                     final DatasetSpecification newSpec) {
    ListenableFuture<ExploreExecutionResult> futureResults = getResultsFuture(new HandleProducer() {
      @Override
      public QueryHandle getHandle() throws ExploreException, SQLException {
        return doUpdateExploreDataset(datasetInstance, oldSpec, newSpec);
      }
    });

    // Exceptions will be thrown in case of an error in the futureHandle
    return Futures.transform(futureResults, Functions.<Void>constant(null));
  }

  @Override
  public ListenableFuture<Void> enableExploreDataset(final DatasetId datasetInstance) {
    ListenableFuture<ExploreExecutionResult> futureResults = getResultsFuture(new HandleProducer() {
      @Override
      public QueryHandle getHandle() throws ExploreException, SQLException {
        return doEnableExploreDataset(datasetInstance, null, false);
      }
    });

    // Exceptions will be thrown in case of an error in the futureHandle
    return Futures.transform(futureResults, Functions.<Void>constant(null));
  }

  @Override
  public ListenableFuture<Void> enableExploreDataset(final DatasetId datasetInstance,
                                                     final DatasetSpecification spec,
                                                     final boolean truncating) {
    ListenableFuture<ExploreExecutionResult> futureResults = getResultsFuture(new HandleProducer() {
      @Override
      public QueryHandle getHandle() throws ExploreException, SQLException {
        return doEnableExploreDataset(datasetInstance, spec, truncating);
      }
    });

    // Exceptions will be thrown in case of an error in the futureHandle
    return Futures.transform(futureResults, Functions.<Void>constant(null));
  }

  @Override
  public ListenableFuture<Void> enableExploreStream(final StreamId stream, final String tableName,
                                                    final FormatSpecification format) {
    ListenableFuture<ExploreExecutionResult> futureResults = getResultsFuture(new HandleProducer() {
      @Override
      public QueryHandle getHandle() throws ExploreException, SQLException {
        return doEnableExploreStream(stream, tableName, format);
      }
    });

    // Exceptions will be thrown in case of an error in the futureHandle
    return Futures.transform(futureResults, Functions.<Void>constant(null));
  }

  @Override
  public ListenableFuture<Void> disableExploreStream(final StreamId stream, final String tableName) {
    ListenableFuture<ExploreExecutionResult> futureResults = getResultsFuture(new HandleProducer() {
      @Override
      public QueryHandle getHandle() throws ExploreException, SQLException {
        return doDisableExploreStream(stream, tableName);
      }
    });

    // Exceptions will be thrown in case of an error in the futureHandle
    return Futures.transform(futureResults, Functions.<Void>constant(null));
  }

  @Override
  public ListenableFuture<Void> addPartition(final DatasetId datasetInstance,
                                             final DatasetSpecification spec,
                                             final PartitionKey key, final String path) {
    ListenableFuture<ExploreExecutionResult> futureResults = getResultsFuture(new HandleProducer() {
      @Override
      public QueryHandle getHandle() throws ExploreException, SQLException {
        return doAddPartition(datasetInstance, spec, key, path);
      }
    });

    // Exceptions will be thrown in case of an error in the futureHandle
    return Futures.transform(futureResults, Functions.<Void>constant(null));
  }

  @Override
  public ListenableFuture<Void> dropPartition(final DatasetId datasetInstance,
                                              final DatasetSpecification spec,
                                              final PartitionKey key) {
    ListenableFuture<ExploreExecutionResult> futureResults = getResultsFuture(new HandleProducer() {
      @Override
      public QueryHandle getHandle() throws ExploreException, SQLException {
        return doDropPartition(datasetInstance, spec, key);
      }
    });

    // Exceptions will be thrown in case of an error in the futureHandle
    return Futures.transform(futureResults, Functions.<Void>constant(null));
  }

  @Override
  public ListenableFuture<ExploreExecutionResult> submit(final NamespaceId namespace, final String statement) {
    return getResultsFuture(new HandleProducer() {
      @Override
      public QueryHandle getHandle() throws ExploreException, SQLException {
        return execute(namespace, statement);
      }
    });
  }

  @Override
  public ListenableFuture<ExploreExecutionResult> columns(@Nullable final String catalog,
                                                          @Nullable final String schemaPattern,
                                                          final String tableNamePattern,
                                                          final String columnNamePattern) {
    return getResultsFuture(new HandleProducer() {
      @Override
      public QueryHandle getHandle() throws ExploreException, SQLException {
        return getColumns(catalog, schemaPattern, tableNamePattern, columnNamePattern);
      }
    });
  }

  @Override
  public ListenableFuture<ExploreExecutionResult> catalogs() {
    return getResultsFuture(new HandleProducer() {
      @Override
      public QueryHandle getHandle() throws ExploreException, SQLException {
        return getCatalogs();
      }
    });
  }

  @Override
  public ListenableFuture<ExploreExecutionResult> schemas(@Nullable final String catalog,
                                                          @Nullable final String schemaPattern) {
    return getResultsFuture(new HandleProducer() {
      @Override
      public QueryHandle getHandle() throws ExploreException, SQLException {
        return getSchemas(catalog, schemaPattern);
      }
    });
  }

  @Override
  public ListenableFuture<ExploreExecutionResult> functions(@Nullable final String catalog,
                                                            @Nullable final String schemaPattern,
                                                            final String functionNamePattern) {
    return getResultsFuture(new HandleProducer() {
      @Override
      public QueryHandle getHandle() throws ExploreException, SQLException {
        return getFunctions(catalog, schemaPattern, functionNamePattern);
      }
    });
  }

  @Override
  public ListenableFuture<MetaDataInfo> info(final MetaDataInfo.InfoType infoType) {
    return executor.submit(new Callable<MetaDataInfo>() {
      @Override
      public MetaDataInfo call() throws Exception {
        return getInfo(infoType);
      }
    });
  }

  @Override
  public ListenableFuture<ExploreExecutionResult> tables(@Nullable final String catalog,
                                                         @Nullable final String schemaPattern,
                                                         final String tableNamePattern,
                                                         @Nullable final List<String> tableTypes) {
    return getResultsFuture(new HandleProducer() {
      @Override
      public QueryHandle getHandle() throws ExploreException, SQLException {
        return getTables(catalog, schemaPattern, tableNamePattern, tableTypes);
      }
    });
  }

  @Override
  public ListenableFuture<ExploreExecutionResult> tableTypes() {
    return getResultsFuture(new HandleProducer() {
      @Override
      public QueryHandle getHandle() throws ExploreException, SQLException {
        return getTableTypes();
      }
    });
  }

  @Override
  public ListenableFuture<ExploreExecutionResult> dataTypes() {
    return getResultsFuture(new HandleProducer() {
      @Override
      public QueryHandle getHandle() throws ExploreException, SQLException {
        return getTypeInfo();
      }
    });
  }

  @Override
  public ListenableFuture<ExploreExecutionResult> addNamespace(final NamespaceMeta namespace) {
    return getResultsFuture(new HandleProducer() {
      @Override
      public QueryHandle getHandle() throws ExploreException, SQLException {
        return createNamespace(namespace);
      }
    });
  }

  @Override
  public ListenableFuture<ExploreExecutionResult> removeNamespace(final NamespaceId namespace) {
    return getResultsFuture(new HandleProducer() {
      @Override
      public QueryHandle getHandle() throws ExploreException, SQLException {
        return deleteNamespace(namespace);
      }
    });
  }

  private ListenableFuture<ExploreExecutionResult> getResultsFuture(final HandleProducer handleProducer) {
    // NOTE: here we have two levels of Future because we want to return the future that actually
    // finishes the execution of the operation - it is not enough that the future handle
    // be available
    ListenableFuture<QueryHandle> futureHandle = executor.submit(new Callable<QueryHandle>() {
      @Override
      public QueryHandle call() throws Exception {
        return handleProducer.getHandle();
      }
    });
    return getFutureResultsFromHandle(futureHandle);
  }

  /**
   * Create a {@link ListenableFuture} object by polling the Explore service using the
   * {@link ListenableFuture} containing a {@link QueryHandle}.
   */
  private ListenableFuture<ExploreExecutionResult> getFutureResultsFromHandle(
    final ListenableFuture<QueryHandle> futureHandle) {

    final StatementExecutionFuture resultFuture = new StatementExecutionFuture(this, futureHandle);
    Futures.addCallback(futureHandle, new FutureCallback<QueryHandle>() {
      @Override
      public void onSuccess(final QueryHandle handle) {
        boolean mustCloseHandle;
        try {
          QueryStatus status = getStatus(handle);
          if (!status.getStatus().isDone()) {
            executor.schedule(new Runnable() {
              @Override
              public void run() {
                onSuccess(handle);
              }
            }, 300, TimeUnit.MILLISECONDS);
            return;
          }
          if (QueryStatus.OpStatus.ERROR.equals(status.getStatus())) {
            throw new SQLException(status.getErrorMessage(), status.getSqlState());
          }
          ExploreExecutionResult result = new ClientExploreExecutionResult(AbstractExploreClient.this, handle, status);
          mustCloseHandle = !resultFuture.set(result) || !status.hasResults();
        } catch (Exception e) {
          mustCloseHandle = true;
          resultFuture.setException(e);
        }
        if (mustCloseHandle) {
          try {
            close(handle);
          } catch (Throwable t) {
            LOG.warn("Failed to close handle {}", handle, t);
          }
        }
      }

      @Override
      public void onFailure(Throwable t) {
        resultFuture.setException(t);
      }
    }, executor);
    return resultFuture;
  }

  /**
   * Interface that produces a handle.
   */
  private interface HandleProducer {
    QueryHandle getHandle() throws ExploreException, SQLException;
  }

  /**
   * Result iterator which polls Explore service using HTTP to get next results.
   */
  private static final class ClientExploreExecutionResult extends AbstractIterator<QueryResult>
    implements ExploreExecutionResult {
    private static final Logger LOG = LoggerFactory.getLogger(ClientExploreExecutionResult.class);
    private static final int DEFAULT_FETCH_SIZE = 100;

    private int fetchSize = DEFAULT_FETCH_SIZE;
    private Iterator<QueryResult> delegate;
    private List<ColumnDesc> resultSchema = null;

    private final ExploreHttpClient exploreClient;
    private final QueryHandle handle;
    private final QueryStatus status;

    ClientExploreExecutionResult(ExploreHttpClient exploreClient, QueryHandle handle, QueryStatus status) {
      this.exploreClient = exploreClient;
      this.handle = handle;
      this.status = status;
    }

    @Override
    public QueryStatus getStatus() {
      return status;
    }

    @Override
    protected QueryResult computeNext() {
      if (!status.hasResults()) {
        return endOfData();
      }

      if (delegate != null && delegate.hasNext()) {
        return delegate.next();
      }
      try {
        // call the endpoint 'next' to get more results and set delegate
        List<QueryResult> nextResults = convertRows(exploreClient.nextResults(handle, fetchSize));
        delegate = nextResults.iterator();

        // At this point, if delegate has no result, there are no more results at all
        if (!delegate.hasNext()) {
          return endOfData();
        }
        return delegate.next();
      } catch (ExploreException e) {
        LOG.error("Exception while iterating through the results of query {}", handle.getHandle(), e);
        throw Throwables.propagate(e);
      } catch (HandleNotFoundException e) {
        // Handle may have timed out, or the handle given is just unknown
        LOG.debug("Received exception", e);
        return endOfData();
      }
    }

    private List<QueryResult> convertRows(List<QueryResult> rows) throws ExploreException {
      List<ColumnDesc> schema = getResultSchema();
      ImmutableList.Builder<QueryResult> builder = ImmutableList.builder();

      for (QueryResult row : rows) {
        Preconditions.checkArgument(row.getColumns().size() == schema.size(), "Row and schema length differ.");
        List<Object> newRow = Lists.newArrayList();
        Iterator<Object> rowIterator = row.getColumns().iterator();
        Iterator<ColumnDesc> schemaIterator = schema.iterator();
        while (rowIterator.hasNext() && schemaIterator.hasNext()) {
          Object columnValue = rowIterator.next();
          ColumnDesc schemaColumn = schemaIterator.next();
          String columnType = schemaColumn.getType();
          if (columnValue != null && columnValue instanceof Double && columnType != null) {
            if (schemaColumn.getType().equals("INT")) {
              columnValue = ((Double) columnValue).intValue();
            } else if (schemaColumn.getType().equals("SMALLINT")) {
              columnValue = ((Double) columnValue).shortValue();
            } else if (schemaColumn.getType().equals("BIGINT")) {
              columnValue = ((Double) columnValue).longValue();
            } else if (schemaColumn.getType().equals("TINYINT")) {
              columnValue = ((Double) columnValue).byteValue();
            }
          } else if ("BINARY".equals(columnType)) {
            // A BINARY value is a byte array, which is deserialized by GSon into a list of
            // double objects - here we recreate a byte[] object.
            List<Object> binary;
            if (columnValue instanceof List) {
              binary = (List) columnValue;
            } else if (columnValue instanceof Double[]) {
              binary = (List) Arrays.asList((Double[]) columnValue);
            } else {
              throw new ExploreException("Unsupported format for BINARY data type: " +
                                           columnValue.getClass().getCanonicalName());
            }
            Object newColumnValue = new byte[binary.size()];
            for (int i = 0; i < ((byte[]) newColumnValue).length; i++) {
              if (!(binary.get(i) instanceof Double)) {
                newColumnValue = columnValue;
                break;
              }
              ((byte[]) newColumnValue)[i] = ((Double) binary.get(i)).byteValue();
            }
            columnValue = newColumnValue;
          } else if ("array<tinyint>".equals(columnType)) {
            // in some versions of hive, a byte[] gets translated to array<tinyint> instead of binary.
            // weirdly enough, in our unit tests, if java6 is used, byte[] fields get changed to array<tinyint>
            // but if java7 is used, byte[] fields get changed to binary...
            // and on top of that it decides to return the byte array as a string... like "[98,111,98]".
            // this entire thing could use a lot of improvement (CDAP-11)
            if (columnValue instanceof String) {
              columnValue = GSON.fromJson((String) columnValue, byte[].class);
            }
          }
          newRow.add(columnValue);
        }
        builder.add(new QueryResult(newRow));
      }
      return builder.build();
    }

    @Override
    public void close() throws IOException {
      try {
        exploreClient.close(handle);
      } catch (HandleNotFoundException e) {
        // Don't need to throw an exception in that case - if the handle is not found, the query is already closed
        LOG.warn("Caught exception when closing the results", e);
      } catch (ExploreException e) {
        LOG.error("Caught exception during close operation", e);
        throw Throwables.propagate(e);
      }
    }

    @Override
    public int getFetchSize() {
      return fetchSize;
    }

    @Override
    public void setFetchSize(int fetchSize) {
      this.fetchSize = (fetchSize <= 0) ? DEFAULT_FETCH_SIZE : fetchSize;
    }

    @Override
    public synchronized List<ColumnDesc> getResultSchema() throws ExploreException {
      if (resultSchema == null) {
        try {
          resultSchema = exploreClient.getResultSchema(handle);
        } catch (HandleNotFoundException e) {
          LOG.error("Caught exception when retrieving results schema", e);
          throw new ExploreException(e);
        }
      }
      return resultSchema;
    }

    @Override
    public boolean canContainResults() {
      return status.hasResults();
    }
  }

  /**
   * Implementation of a listenable future for {@link ExploreExecutionResult} with an overridden
   * {@link com.google.common.util.concurrent.ListenableFuture#cancel(boolean)} method.
   */
  private static final class StatementExecutionFuture extends AbstractFuture<ExploreExecutionResult> {
    private static final Logger LOG = LoggerFactory.getLogger(StatementExecutionFuture.class);

    private final Explore exploreClient;
    private final ListenableFuture<QueryHandle> futureHandle;

    StatementExecutionFuture(Explore exploreClient, ListenableFuture<QueryHandle> futureHandle) {
      this.exploreClient = exploreClient;
      this.futureHandle = futureHandle;
    }

    @Override
    public boolean set(@Nullable ExploreExecutionResult value) {
      return super.set(value);
    }

    @Override
    public boolean setException(Throwable throwable) {
      return super.setException(throwable);
    }

    @Override
    protected void interruptTask() {
      // Cancelling the future object means cancelling the query, as well as closing it
      // Since closing the query also cancels it, we only need to close
      Futures.addCallback(futureHandle, new FutureCallback<QueryHandle>() {
        @Override
        public void onSuccess(QueryHandle handle) {
          try {
            exploreClient.close(handle);
          } catch (ExploreException e) {
            LOG.error("Caught exception during close operation", e);
            throw Throwables.propagate(e);
          } catch (HandleNotFoundException e) {
            // Don't need to throw an exception in that case -
            // if the handle is not found, the query is already closed
            LOG.warn("Caught exception during close operation", e);
          }
        }

        @Override
        public void onFailure(Throwable t) {
          LOG.error("Caught exception", t);
          setException(t);
        }
      });
    }
  }
}
