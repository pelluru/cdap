.. meta::
    :author: Cask Data, Inc.
    :description: Cask Data Application Platform WordCount Application
    :copyright: Copyright © 2015-2016 Cask Data, Inc.

.. _examples-stream-conversion:

=================
Stream Conversion
=================

A Cask Data Application Platform (CDAP) example demonstrating Time-Partitioned FileSets.


Overview
========
This application receives simple events through a stream, and periodically converts these events into
partitions of a time-partitioned file set. These partitions can be queried with SQL. These are the
components of the application:

- The *events* stream receives simple events, where each event body is a number.
- The *converted* dataset is a time-partitioned file set in Avro format.
- The ``StreamConversionMapReduce`` reads the last five minutes of events from the
  stream and writes them to a new partition in the ``converted`` dataset.
- The ``StreamConversionWorkflow`` is scheduled every five minutes and only runs the
  ``StreamConversionMapReduce``.

Let's look at some of these components, and then run the application and see the results.

The Stream Conversion Application
---------------------------------
As in the other :ref:`examples <examples-index>`, the components
of the application are tied together by the class ``StreamConversionApp``:

.. literalinclude:: /../../../cdap-examples/StreamConversion/src/main/java/co/cask/cdap/examples/streamconversion/StreamConversionApp.java
     :language: java
     :lines: 32-

The interesting part is the creation of the dataset *converted*:

- It is a ``TimePartitionedFileSet``. This dataset manages the files in a ``FileSet`` by
  associating each file with a timestamp.
- The properties are divided in two sections:

  - The first set of properties configures the underlying FileSet, as documented in the
    :ref:`FileSet <datasets-fileset>` section.
  - The second set of properties configures how the dataset is queryable with SQL. Here we can enable the
    dataset for querying, and if so, we must specify Hive-specific properties for the Avro format: the Avro
    SerDe, an input and an output format, and an additional table property: namely, the schema for the Avro SerDe.

The MapReduce Program
---------------------
In its ``initialize()`` method, the ``StreamConversionMapReduce`` determines its logical start time,
and it configures the *events* stream as its input and the *converted* dataset as its output:

- This is a map-only MapReduce program; in other words, it has no reducers,
  and the mappers write directly to the output in Avro format:

  .. literalinclude:: /../../../cdap-examples/StreamConversion/src/main/java/co/cask/cdap/examples/streamconversion/StreamConversionMapReduce.java
     :language: java
     :lines: 65-70
     :dedent: 4

- Based on the logical start time, the MapReduce determines the range of events to read from the stream:

  .. literalinclude:: /../../../cdap-examples/StreamConversion/src/main/java/co/cask/cdap/examples/streamconversion/StreamConversionMapReduce.java
     :language: java
     :lines: 72-74
     :dedent: 4

- Each MapReduce run writes its output to a partition with the logical start time:

  .. literalinclude:: /../../../cdap-examples/StreamConversion/src/main/java/co/cask/cdap/examples/streamconversion/StreamConversionMapReduce.java
     :language: java
     :lines: 77-78
     :dedent: 4

- Note that the output file path is derived from the output partition time by the dataset itself:

  .. literalinclude:: /../../../cdap-examples/StreamConversion/src/main/java/co/cask/cdap/examples/streamconversion/StreamConversionMapReduce.java
     :language: java
     :lines: 80-82
     :dedent: 4

- The Mapper itself is straight-forward: for each event, it emits an Avro record:

  .. literalinclude:: /../../../cdap-examples/StreamConversion/src/main/java/co/cask/cdap/examples/streamconversion/StreamConversionMapReduce.java
     :language: java
     :lines: 88-100
     :dedent: 2


.. Building and Starting
.. =====================
.. |example| replace:: StreamConversionApp
.. |example-italic| replace:: *StreamConversionApp*
.. |example-dir| replace:: StreamConversion
.. |example-artifact| replace:: StreamConversion
.. |application-overview-page| replace:: :cdap-ui-apps-programs:`application overview page, programs tab <StreamConversionApp>`

.. include:: _includes/_building-starting-running-example-with-dir.txt


Running the Example
===================

.. Resuming the Schedule
.. ---------------------
.. |example-workflow| replace:: StreamConversionWorkflow
.. |example-workflow-italic| replace:: *StreamConversionWorkflow*
.. |example-schedule| replace:: every5min

.. include:: _includes/_resuming-schedule.txt

Running the Workflow
--------------------
The ``StreamConversionWorkflow`` will run automatically every five minutes based on its schedule.
To give it some data, you can use a provided script to send events to the stream, for example,
to send 10000 events at a rate of roughly two per second (one per second in the case of Windows):

.. tabbed-parsed-literal::

  .. Linux

  $ examples/StreamConversion/bin/send-events.sh --events 10000 --delay 0.5

  .. Windows

  > examples\StreamConversion\bin\send-events.bat 10000 1

You can now wait for the workflow to run, after which you can query the partitions in the
*converted* dataset:

.. tabbed-parsed-literal::

  $ cdap cli execute "\"show partitions dataset_converted\""

  +============================================+
  | partition: STRING                          |
  +============================================+
  | year=2015/month=1/day=28/hour=17/minute=30 |
  | year=2015/month=1/day=28/hour=17/minute=35 |
  | year=2015/month=1/day=28/hour=17/minute=40 |
  +============================================+

Note that in the Hive meta store, the partitions are registered with multiple dimensions rather
than the time since the Epoch: the year, month, and day of the month plus the hour and minute of the day.

You can also query the data in the dataset. For example, to find the five most frequent body texts, issue:

.. tabbed-parsed-literal::

  $ cdap cli execute "\"select count(*) as count, body from dataset_converted group by body order by count desc limit 5\""

  +==============================+
  | count: BIGINT | body: STRING |
  +==============================+
  | 86            | 53           |
  | 81            | 92           |
  | 75            | 45           |
  | 73            | 24           |
  | 70            | 63           |
  +==============================+

Because this dataset is time-partitioned, you can use the partitioning keys to restrict the scope
of the query. For example, to run the same query for only the month of January, use the query::

  select count(*) as count, body from dataset_converted where month=1 group by body order by count desc limit 5


.. Stopping and Removing the Application
.. =====================================
.. include:: _includes/_stopping-removing-application-title.txt

.. include:: _includes/_suspending-schedule.txt

.. include:: _includes/_removing-application.txt
