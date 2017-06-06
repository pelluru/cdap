.. meta::
    :author: Cask Data, Inc.
    :description: Cask Data Application Platform WordCount Application
    :copyright: Copyright © 2014-2017 Cask Data, Inc.

.. _examples-sport-results:

=============
Sport Results
=============

A Cask Data Application Platform (CDAP) example demonstrating partitioned file sets through sport results analytics.


Overview
========
This application demonstrates the use of the ``PartitionedFileSet`` datasets, 
MapReduce with runtime arguments, and ad-hoc queries over file sets:

- Game results are stored in the ``PartitionedFileSet`` *results*. It is partitioned by league and season,
  and each partition is a CSV (comma-separated values) file containing the results in one league for a season;
  for example, the 2014 season of the NFL (National Football League).
- Results are uploaded into the file set using the *UploadService*.
- The ``ScoreCounter`` MapReduce program reads game results for a given league and
  aggregates total counts such as games won and lost, or points scored and conceded, across all
  seasons, and writes these totals to the partitioned file set *totals* that is partitioned by league.
- Both the original game results and the aggregated totals can be explored using ad-hoc SQL queries.

Let's look at some of these components, and then run the application and see the results.

The SportResults Application
----------------------------
As in the other :ref:`examples <examples-index>`, the components
of the application are tied together by the class ``SportResults``:

.. literalinclude:: /../../../cdap-examples/SportResults/src/main/java/co/cask/cdap/examples/sportresults/SportResults.java
    :language: java
    :lines: 29-

The ``configure()`` method creates the two PartitionedFileSet datasets used in this example.

- Both datasets use CSV as the format: for MapReduce, they use the ``TextInputFormat`` and ``TextOutputFormat``
  with a comma ("``,``") as the field separator; for Explore, they use the ``csv`` format.
- The first dataset (*results*) is partitioned by league and season. Each record represents
  a single game with a date, a winning and a losing team, and the winner's and the loser's points, for example::

       2011/9/5,Dallas Cowboys,New York Giants,24,17
       2011/9/9,Philadelphia Eagles,Cleveland Browns,17,16
       2011/9/9,New England Patriots,Tennessee Titans,34,13

  We have included some sample data in the ``resources`` directory. Note that because the
  column name ``date`` is a `Hive reserved keyword
  <https://cwiki.apache.org/confluence/display/Hive/LanguageManual+DDL#LanguageManualDDL-Keywords,Non-reservedKeywordsandReservedKeywords>`__, 
  it has been enclosed in single back-ticks in the Explore schema declaration.
- The *totals* dataset stores aggregates across all seasons and thus has the league as its single
  partitioning field. Each record has, for an individual team, the total number of games won and lost
  and the total number of points scored and conceded.

We will use the *UploadService* to upload the sample data files into the *results* dataset,
then compute the *totals* aggregates using MapReduce, and we will explore both datasets using SQL.

UploadService
-------------
This service has two handler methods: one to upload and another to download a partition of the *results*
dataset as a file. It declares its use of the dataset using a ``@UseDataSet`` annotation:

.. literalinclude:: /../../../cdap-examples/SportResults/src/main/java/co/cask/cdap/examples/sportresults/UploadService.java
    :language: java
    :lines: 71-72
    :dedent: 4

Let's take a closer look at the upload method:

- It first creates a partition key from the league and season received as path parameters in the request URL.
- Then it obtains a ``PartitionOutput`` for that partition key from the *results* dataset.
- It then uses the ``getLocation`` method of the PartitionOutput to obtain the location
  for writing the file, and opens an output stream for that location to write the file contents.
  ``Location`` is a file system abstraction from `Apache™ Twill® <http://twill.apache.org>`__;
  you can read more about its interface in the `Apache Twill
  Javadocs <http://twill.apache.org/apidocs/org/apache/twill/filesystem/Location.html>`__.
- It then returns an ``HttpContentConsumer`` to consume the incoming request body.

  - In the ``onReceive`` method, it keeps appending newly received bytes to the output stream.
  - In the ``onFinish`` method, it registers the written file as a new partition in the dataset,
    by calling the ``addPartition`` method of the PartitionOutput.
  - In the ``onError`` method, it does cleanup by removing the partially written file and responds with an error status.

.. literalinclude:: /../../../cdap-examples/SportResults/src/main/java/co/cask/cdap/examples/sportresults/UploadService.java
    :language: java
    :lines: 105-171
    :dedent: 4


MapReduce over File Partitions
==============================
``ScoreCounter`` is a simple MapReduce that reads from the *results* PartitionedFileSet and writes to
the *totals* PartitionedFileSet. The ``initialize`` method prepares the MapReduce program for this:

- It reads the league that it is supposed to process from the runtime arguments.
- It constructs a partition filter for the input using the league as the only condition, and instantiates
  the *results* dataset with arguments that contain this filter.
- It constructs an output partition key for the new partition, and instantiates the *totals* dataset
  with arguments specifying that partition key.

.. literalinclude:: /../../../cdap-examples/SportResults/src/main/java/co/cask/cdap/examples/sportresults/ScoreCounter.java
    :language: java
    :lines: 48-86
    :append: ...

It is worth mentioning that nothing else in ``ScoreCounter`` is specifically programmed to use file partitions.
Instead of *results* and *totals*, it could use any other dataset as long as the key and value types match.


.. Building and Starting
.. =====================
.. |example| replace:: SportResults
.. |example-italic| replace:: *SportResults*
.. |application-overview-page| replace:: :cdap-ui-apps-programs:`application overview page, programs tab <SportResults>`

.. include:: _includes/_building-starting-running.txt


Running the Example
===================

.. Starting the Service
.. --------------------
.. |example-service| replace:: UploadService
.. |example-service-italic| replace:: *UploadService*

.. include:: _includes/_starting-service.txt

Uploading Game Results
----------------------
Begin by uploading some CSV files into the *results* dataset. For example, to upload the results
for the 2012 season of the NFL (National Football League):

.. tabbed-parsed-literal::

  $ cdap cli call service SportResults.UploadService PUT leagues/nfl/seasons/2012 body:file examples/SportResults/resources/nfl-2012.csv
  
Using a ``curl``  command:

.. tabbed-parsed-literal::
 
  $ curl -w"\n" -X PUT localhost:11015/v3/namespaces/default/apps/SportResults/services/UploadService/methods/leagues/nfl/seasons/2012 \
  --data-binary @examples/SportResults/resources/nfl-2012.csv


Feel free to add more seasons |---| and sport leagues:

.. tabbed-parsed-literal::

  $ cdap cli call service SportResults.UploadService PUT leagues/nfl/seasons/2013 body:file examples/SportResults/resources/nfl-2013.csv
  
  $ cdap cli call service SportResults.UploadService PUT leagues/nba/seasons/2012 body:file examples/SportResults/resources/nba-2012.csv

  $ cdap cli call service SportResults.UploadService PUT leagues/nba/seasons/2013 body:file examples/SportResults/resources/nba-2013.csv
  
Note that the files can only be uploaded once for each season and league. A subsequent upload would fail because the file partition already exists.

Starting the MapReduce
----------------------
To run the ``ScoreCounter`` over all seasons of the NFL:

.. tabbed-parsed-literal::

  $ cdap cli start mapreduce SportResults.ScoreCounter league=nfl
  
Note that the MapReduce can only be run once for each league. A subsequent run would fail because the output already exists.

Exploring with Ad-hoc SQL
-------------------------

Both of the partitioned file sets are registered as external tables in Hive and can be explored with SQL. To
see the existing partitions of a dataset, use the ``show partitions`` query:

.. tabbed-parsed-literal::

  $ cdap cli execute "\"show partitions results\""

For example, to find the three games with the highest point difference in the 2012 NFL season, over all
seasons (that have been uploaded), and for all seasons of all sport leagues:

.. tabbed-parsed-literal::

  $ cdap cli execute "\"select * from results where league='nfl' and season=2012 order by winnerpoints-loserpoints desc limit 3\""

  $ cdap cli execute "\"select * from results where league='nfl' order by winnerpoints-loserpoints desc limit 3\""

  $ cdap cli execute "\"select * from results order by winnerpoints-loserpoints desc limit 3\""

You can also explore the *totals* dataset. For example, to find the NFL teams team that, over their history,
have scored the least points compared to the points they conceded:

.. tabbed-parsed-literal::

  $ cdap cli execute "\"select * from totals where league = 'nfl' order by conceded - scored desc limit 3"\"
  
The last command would produce results (your results may vary, depending on the datasets you load) such as::

  Successfully connected CDAP instance at http://localhost:11015/default
  +===============================================================================================================================+
  | totals.team: ST | totals.wins: IN | totals.ties: IN | totals.losses:  | totals.scored:  | totals.conceded | totals.league: ST |
  | RING            | T               | T               | INT             | INT             | : INT           | RING              |
  +===============================================================================================================================+
  | Jacksonville Ja | 6               | 0               | 26              | 502             | 893             | nfl               |
  | guars           |                 |                 |                 |                 |                 |                   |
  |-------------------------------------------------------------------------------------------------------------------------------|
  | Oakland Raiders | 8               | 0               | 24              | 612             | 896             | nfl               |
  |-------------------------------------------------------------------------------------------------------------------------------|
  | New York Jets   | 14              | 0               | 18              | 571             | 762             | nfl               |
  +===============================================================================================================================+
  Fetched 3 rows


.. Stopping and Removing the Application
.. =====================================
.. include:: _includes/_stopping-service-removing-application.txt
