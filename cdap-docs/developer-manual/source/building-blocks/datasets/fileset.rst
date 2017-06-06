.. meta::
    :author: Cask Data, Inc.
    :copyright: Copyright © 2014-2017 Cask Data, Inc.

.. _datasets-fileset:

===============
FileSet Dataset
===============

.. highlight:: java

While real-time programs such as flows normally require datasets with random access, batch-oriented
programming paradigms such as MapReduce are more suitable for data that can be read and written sequentially.
The most prominent form of such data is an HDFS file, and MapReduce is highly optimized for such files.
CDAP's abstraction for files is the *FileSet* dataset.

A *FileSet* represents a set of files on the file system that share certain properties:

- The location in the file system. All files in a FileSet are located relative to a
  base path, which is created when the FileSet is created. Deleting the
  FileSet will also delete this directory and all the files it contains.
- The Hadoop input and output format. They are given as dataset properties by their
  class names.  When a FileSet is used as the input or output of a MapReduce program,
  these classes are injected into the Hadoop configuration by the CDAP runtime
  system.
- Additional properties of the specified input and output format. Each format has its own 
  properties; consult the format's documentation for details. For example, the
  ``TextOutputFormat`` allows configuring the field separator character by setting the
  property ``mapreduce.output.textoutputformat.separator``. These properties are also set
  into the Hadoop configuration by the CDAP runtime system.

These properties are configured at the time the FileSet is created. They apply to all
files in the dataset. Every time you use a FileSet in your application code, you can
address either the entire dataset or, by specifying its relative path as a runtime argument,
an individual file in the dataset. Specifying an individual file is only supported for
MapReduce programs.

Creating a FileSet
==================

To create and use a FileSet in an application, you create it as part of the application configuration::

  public class FileSetExample extends AbstractApplication {

    @Override
    public void configure() {
      ...
      createDataset("lines", FileSet.class, FileSetProperties.builder()
        .setBasePath("example/data/lines")
        .setInputFormat(TextInputFormat.class)
        .setOutputFormat(TextOutputFormat.class)
        .setOutputProperty(TextOutputFormat.SEPERATOR, ":")
        .build());
      ...
    }

This creates a new FileSet named *lines* that uses ``TextInputFormat`` and ``TextOutputFormat.``
For the output format, we specify an additional property to make it use a colon as the separator
between the key and the value in each line of output.

Input and output formats must be implementations of the standard Apache Hadoop
`InputFormat <https://hadoop.apache.org/docs/current/api/org/apache/hadoop/mapreduce/InputFormat.html>`_
and
`OutputFormat <https://hadoop.apache.org/docs/current/api/org/apache/hadoop/mapreduce/OutputFormat.html>`_
specifications. If you do not specify an input format, you will not be able to use this as the input for a
MapReduce program; similarly for the output format.

If you do not specify a base path, the dataset framework will generate a path based on the dataset name.
This path |---| and any relative base path you specify |---| is relative to the data directory of the CDAP namespace
in which the FileSet is created. You can also specify an absolute base path (one that begins with the character ``/``).
This path is interpreted as an absolute path in the file system. Beware that if you create two FileSets with the
same base path |---| be it multiple FileSets in the same namespace with the same relative base path, or in different
namespaces with the same absolute base path |---| then these multiple FileSets will use the same directory and possibly
obstruct each other's operations.

You can configure a FileSet as "external". This means that the data (the actual files) in
the FileSet are managed by an external process. This allows you to use FileSets with
existing locations outside of CDAP. In that case, the FileSet will not allow the writing
or deleting of files: it treats the contents of the base path as read-only::

      createDataset("lines", FileSet.class, FileSetProperties.builder()
        .setBasePath("/existing/path")
        .setDataExternal(true)
        .setInputFormat(TextInputFormat.class)
        ...

If you want to use an existing location and still be able to write to it, you have two options:

.. _datasets-fileset-reuse:

- ``setUseExisting(true)``: This directs the FileSet to accept an existing location as its base
  path and an existing table in Hive for exploring. However, because the existing location may
  contain files prior to the FileSet creation, the location and the Hive table will not be
  deleted when the dataset is dropped, and truncating the FileSet will have no effect. 
  This is to ensure that no pre-existing data is deleted.

- ``setPossessExisting(true)``: Similarly, this allows reuse of an existing location.
  The FileSet will assume ownership of existing files in that location and of the Hive table,
  which means that those files and the Hive table will be deleted when the dataset is dropped
  or truncated.

Using a FileSet in MapReduce
============================

Using a FileSet as input or output of a MapReduce program is the same as for any other dataset::

  public class WordCount extends AbstractMapReduce {

    @Override
    public void initialize() {
      MapReduceContext context = getContext();
      context.addInput(Input.ofDataset("lines"));
      context.addOutput(Output.ofDataset("counts"));
      ...
    }

The MapReduce program only needs to specify the names of the input and output datasets.
Whether they are FileSets or another type of dataset is handled by the CDAP runtime system.

.. highlight:: console

However, you do need to tell CDAP the relative paths of the input and output files. Currently,
this is only possible by specifying them as runtime arguments when the MapReduce program is started:

.. tabbed-parsed-literal::

  $ curl -w"\n" -X POST "http://example.com:11015/v3/namespaces/default/apps/FileSetExample/mapreduce/WordCount/start" \
  -d '{ "dataset.lines.input.paths": "monday/my.txt", "dataset.counts.output.path": "monday/counts.out" }'
          
Using the CDAP CLI:

.. tabbed-parsed-literal::
    :tabs: "CDAP CLI"
    
    |cdap >| start mapreduce FileSetExample.WordCount "dataset.lines.input.paths=monday/my.txt dataset.counts.output.path=monday/counts.out"

Note that for the input you can specify multiple paths separated by commas::

      "dataset.lines.input.paths": "monday/lines.txt,tuesday/lines.txt"

If you do not specify both the input and output paths, your MapReduce program will fail with an error.

.. highlight:: java

Using a FileSet Programmatically
================================

You can interact with the files of a FileSet directly, through the ``Location`` abstraction
of the file system. For example, a Service can use a FileSet by declaring it with a ``@UseDataSet``
annotation, and then obtaining a ``Location`` for a relative path within the FileSet::

    @UseDataSet("lines")
    private FileSet lines;

    @GET
    @Path("{fileSet}")
    public void read(HttpServiceRequest request, HttpServiceResponder responder,
                     @QueryParam("path") String filePath) {

      Location location = lines.getLocation(filePath);
      try {
        InputStream inputStream = location.getInputStream();
        ...
      } catch (IOException e) {
        ...
      }
    }

See the Apache™ Twill®
`API documentation <http://twill.apache.org/apidocs/org/apache/twill/filesystem/Location.html>`__
for additional information about the ``Location`` abstraction.

Exploring FileSets
==================

A file set can be explored with ad-hoc queries if you enable it at creation time;
this is described under :ref:`fileset-exploration`.
