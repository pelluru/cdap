.. meta::
    :author: Cask Data, Inc.
    :copyright: Copyright © 2014-2017 Cask Data, Inc.

======
Tables
======

.. highlight:: java

The ``Table`` API provides basic methods to perform read, write and delete operations,
plus special scan, atomic increment and compare-and-swap operations::

  // Read
  public Row get(Get get)
  public Row get(byte[] row)
  public byte[] get(byte[] row, byte[] column)
  public Row get(byte[] row, byte[][] columns)
  public Row get(byte[] row, byte[] startColumn,
                 byte[] stopColumn, int limit)

  // Scan
  public Scanner scan(byte[] startRow, byte[] stopRow)

  // Write
  public void put(Put put)
  public void put(byte[] row, byte[] column, byte[] value)
  public void put(byte[] row, byte[][] columns, byte[][] values)

  // Compare And Swap
  public boolean compareAndSwap(byte[] row, byte[] column,
                                byte[] expectedValue, byte[] newValue)

  // Increment and return result
  public Row incrementAndGet(Increment increment)
  public long incrementAndGet(byte[] row, byte[] column, long amount)
  public Row incrementAndGet(byte[] row, byte[][] columns, long[] amounts)

  // Increment without result
  public void increment(Increment increment)
  public void increment(byte[] row, byte[] column, long amount)
  public void increment(byte[] row, byte[][] columns, long[] amounts)

  // Delete
  public void delete(Delete delete)
  public void delete(byte[] row)
  public void delete(byte[] row, byte[] column)
  public void delete(byte[] row, byte[][] columns)

Each basic operation has a method that takes an operation-type object as a parameter
plus handy methods for working directly with byte arrays.
If your application code already deals with byte arrays, you can use the latter methods to save a conversion.

Read
====
A ``get`` operation reads all columns or a selection of columns of a single row::

  Table t;
  byte[] rowKey1;
  byte[] columnX;
  byte[] columnY;
  int n;

  // Read all columns of a row
  Row row = t.get(new Get("rowKey1"));

  // Read specified columns from a row
  Row rowSelection = t.get(new Get("rowKey1").add("column1").add("column2"));

  // Reads a column range from x (inclusive) to y (exclusive)
  // with a limit of n return values
  rowSelection = t.get(rowKey1, columnX, columnY; n);

  // Read only one column in one row byte[]
  value = t.get(rowKey1, columnX);

The ``Row`` object provides access to the row data including its columns. If only a
selection of row columns is requested, the returned ``Row`` object will contain only these columns.
The ``Row`` object provides an extensive API for accessing returned column values::

  // Get column value as a byte array
  byte[] value = row.get("column1");

  // Get column value of a specific type
  String valueAsString = row.getString("column1");
  Integer valueAsInteger = row.getInt("column1");

When requested, the value of a column is converted to a specific type automatically.
If the column is absent in a row, the returned value is ``null``. To return primitive types,
the corresponding methods accept a default value to be returned when the column is absent::

  // Get column value as a primitive type or 0 if column is absent
  long valueAsLong = row.getLong("column1", 0);

Multiple rows can be requested together using a variation of the ``get`` operation that takes a
list of ``Get`` objects to be retrieved::

  Table t;

  // Define the rows to retrieve
  List<Get> gets = Lists.newArrayList();
  gets.add(new Get("row1"));
  // Separate columns can be requested for each row
  gets.add(new Get("row2").add("column1").add("column2"));
  gets.add(new Get("row3"));

  List<Row> rows = t.get(gets);

Each ``Row`` object in the returned list will contain the results for one of the requested row
keys.  When multiple rows must be retrieved together, this version of the ``get`` operation
allows the storage provider to perform more efficient batching of the operations, if supported.

Scan
====
A ``scan`` operation fetches a subset of rows or all of the rows of a Table::

  byte[] startRow;
  byte[] stopRow;
  Row row;

  // Scan all rows from startRow (inclusive) to
  // stopRow (exclusive)
  try (Scanner scanner = t.scan(startRow, stopRow)) {
    while ((row = scanner.next()) != null) {
      LOG.info("column1: " + row.getString("column1", "null"));
    }
  }

To scan a set of rows not bounded by ``startRow`` and/or ``stopRow``
you can pass ``null`` as their value::

  byte[] startRow;
  // Scan all rows of a table
  Scanner allRows = t.scan(null, null);
  // Scan all columns up to stopRow (exclusive)
  Scanner headRows = t.scan(null, stopRow);
  // Scan all columns starting from startRow (inclusive)
  Scanner tailRows = t.scan(startRow, null);

Write
=====
A ``put`` operation writes data into a row::

  // Write a set of columns with their values
  t.put(new Put("rowKey1").add("column1", "value1").add("column2", 55L));

Note that the column value cannot be empty, that is, it must have at least length one.

Compare and Swap
================
A swap operation compares the existing value of a column with an expected value,
and if it matches, replaces it with a new value.
The operation returns ``true`` if it succeeds and ``false`` otherwise::

  byte[] expectedCurrentValue;
  byte[] newValue;
  if (!t.compareAndSwap(rowKey1, columnX,
        expectedCurrentValue, newValue)) {
    LOG.info("Current value was different from expected");
  }

Note that the column value cannot be empty, that is, it must have at least length one.

Increment
=========
An increment operation increments a ``long`` value of one or more columns by either ``1L``
or an integer amount *n*.  If a column does not exist, it is created with an assumed value of zero
before the increment is applied::

  // Write long value to a column of a row
  t.put(new Put("rowKey1").add("column1", 55L));
  // Increment values of several columns in a row
  t.increment(new Increment("rowKey1").add("column1", 1L).add("column2", 23L));

If the existing value of the column cannot be converted to a ``long``,
a ``NumberFormatException`` will be thrown.

Two types of increment operations are supported:

- ``incrementAndGet(...)`` operations will increment the currently stored value and return the
  result; and
- ``increment(...)`` operations will increment the currently stored value without any return
  value.

Read-less Increments
--------------------
By default, an increment operation will need to first perform a read operation to find the
currently stored column value, apply the increment to the stored value, and then write the final
result.  For high write volume workloads, with only occassional reads, this can impose a great
deal of unnecessary overhead for increments.

In these situations, you can configure the dataset to support read-less increments.  With read-less
increments, each operation only performs a write operation, storing the incremental value for the
column in a new cell.  This completely eliminates the cost of the read operation when performing
increments.  Instead, when reading the value for a column storing data for read-less increments,
all of the stored increment values are read and summed up together with the last stored complete
sum, in order to compute the final result.  As a result, read operations become more expensive, but
this trade-off can be very beneficial for workloads dominated by writes.

Read-less increments can only be used with the ``increment(...)`` operation, since it does not
return a value.  To configure a dataset to support read-less increments:

1. Set the property ``dataset.table.readless.increment`` to ``true`` in the Dataset properties.
2. Use the ``increment(...)`` methods for any operations that do not need the result value of the
   increment operation.

*Note:* the current implementation of read-less increments uses an HBase coprocessor to prefix the
stored values for incremental updates with a special prefix.  Since this prefix could occur
naturally in other stored data values, it is highly recommended that increments be stored in a
separate dataset and not be mixed in with other types of values.  This will ensure that other data is
not mis-identified as a stored increment and prevent incorrect results.

Delete
======
A delete operation removes an entire row or a subset of its columns::

  // Delete the entire row
  t.delete(new Delete("rowKey1"));
  // Delete a selection of columns from the row
  t.delete(new Delete("rowKey1").add("column1").add("column2"));

Note that specifying a set of columns helps to perform delete operation faster.
When you want to delete all the columns of a row and you know all of them,
passing all of them will make the deletion faster. Deleting all the columns of a row will
also delete the entire row, as the underlying implementation of a Table is a 
`columnar store. <http://en.wikipedia.org/wiki/Column-oriented_DBMS>`__

Writing from MapReduce
======================
Table implements the ``BatchWritable`` interface, using ``byte[]`` as the key and
``Put`` as the value for each write. To write to a table from MapReduce, use these
types as the output types of your Reducer (or Mapper in case of a map-only program).
For example, the Reducer can be defined as follows::

  /**
   * A reducer that sums up the counts for each key.
   */
  public static class Counter extends Reducer<Text, IntWritable, byte[], Put> {

    @Override
    public void reduce(Text key, Iterable<IntWritable> values, Context context)
      throws IOException, InterruptedException {
      long sum = 0L;
      for (IntWritable value : values) {
        sum += value.get();
      }
      byte[] row = Bytes.toBytes(key.toString());
      context.write(row, new Put(row).add("count", sum));
    }
  }

Note that here, the key is always ignored, because the ``Put`` that is provided as the value also
contains the row key. It would therefore also be safe to write null for the key::

      context.write(null, new Put(row).add("count", sum));

.. _table-datasets-pre-splitting:

Pre-Splitting a Table into Multiple Regions
===========================================

When the underlying storage for a Table Dataset (or any Dataset that uses a Table
underneath, such as a ``KeyValueTable``) is HBase, CDAP allows you to 
`configure pre-splitting <http://hbase.apache.org/book.html#manual_region_splitting_decisions>`__
to gain a better distribution of data operations after the tables are created. This helps
optimize for better performance, depending on your use case.

To specify the splits for a Table-based Dataset, you use the ``hbase.splits`` dataset property. 
The value must contain a JSON-formatted ``byte[][]`` of the split keys, such as::

  { "hbase.splits": "[[64],[128],[192]]" }

The above will create four regions; the first of which will receive all rows whose first
byte is in the range 0…63; the second will receive the range 64…127, the third will
receive the range 128…191 and the fourth will receive the range 192…255.

You set Dataset properties when you create the Dataset, either during application
deployment or via CDAP's HTTP RESTful APIs. The following is an example of the former; for
an example of the latter, please refer to the 
:ref:`Dataset section <http-restful-api-dataset-creating>` of the :ref:`RESTful API
:Reference <restful-api>`.

To configure pre-splitting for a Table created during application deployment, in your
application class' ``configure()`` you specify::

  public class MyApp extends AbstractApplication {

    @Override
    public void configure() {
      DatasetProperties props = 
          DatasetProperties.builder().add("hbase.splits", "[[64],[128],[192]]").build();
      createDataset("myTable", KeyValueTable.class, props);
      
      // init other components
      
    }
  } 
