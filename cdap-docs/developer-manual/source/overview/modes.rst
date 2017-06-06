.. meta::
    :author: Cask Data, Inc.
    :copyright: Copyright © 2014-2017 Cask Data, Inc.

=========================
CDAP Modes and Components
=========================

.. _modes-data-application-platform:

Runtime Modes
=============
The Cask Data Application Platform (CDAP) can be run in different modes:

- **Distributed CDAP** for staging and production;
- **CDAP Sandbox** for testing and development on a developer's laptop; or
- **In-Memory CDAP** for unit testing and continuous integration pipelines.

Regardless of the runtime mode, CDAP is fully-functional and the code you develop never
changes. However, performance and scale are limited when using in-memory or local sandbox
CDAPs. CDAP Applications are developed against the CDAP APIs, making the switch between
these modes seamless. An application developed using a given mode can easily run in
another mode.


.. _distributed-data-application-platform:

.. rubric:: Distributed CDAP

The **Distributed CDAP** runs in fully distributed mode. In addition to the system components
of the CDAP, distributed and highly available (HA) deployments of the underlying Hadoop
infrastructure are included. Production applications should always be run on a Distributed
CDAP.

See the instructions for either a :ref:`distribution-specific <installation-index>` or
:ref:`generic Apache Hadoop <admin-installation-packages>` cluster for more information.

**Features:**

- A production, staging, and QA mode; runs in a distributed environment
- Currently uses Apache HBase and HDFS as the underlying storage technology
- Uses Apache YARN Containers as the processing abstraction (via `Apache™ Twill® <http://twill.apache.org>`__)


.. _sandbox-data-application-platform:

.. rubric:: CDAP Sandbox

The **CDAP Sandbox** allows you to run the entire CDAP stack in a single Java Virtual
Machine on your local machine and includes a local version of the CDAP UI. The
underlying Big Data infrastructure is emulated on top of your local file system. All data
is persisted.

See :ref:`Getting Started Developing <getting-started-index>` and :ref:`CDAP Sandbox
<sandbox-index>` for information on how to start and manage your CDAP Sandbox.

**Features:**

- Designed to run in a sandbox environment, for development and testing
- Uses LevelDB/Local File System as the storage technology
- Uses Java Threads as the processing abstraction (via `Apache™ Twill® <http://twill.apache.org>`__)


.. _in-memory-data-application-platform:

.. rubric:: In-Memory CDAP

The **In-Memory CDAP** allows you to easily run CDAP for use in unit tests and continuous
integration (CI) pipelines. In this mode, the underlying Big Data infrastructure is
emulated using in-memory data structures and there is no persistence. The CDAP UI is not
available in this mode.

See :ref:`test-cdap` for information and examples on using this mode.

**Features:**

- Purpose-built for writing unit tests and CI pipelines
- Mimics storage technologies as in-memory data structures; for example,
  `Java NavigableMap <http://docs.oracle.com/javase/7/docs/api/java/util/NavigableMap.html>`__
- Uses Java Threads as the processing abstraction (via `Apache™ Twill® <http://twill.apache.org>`__)


Components
==========
This diagram illustrates the components that comprise Distributed CDAP and shows some of their interactions,
with CDAP system components in orange and non-system components in yellow and grey:

.. image:: ../_images/arch_components_view.png
   :width: 6in
   :align: center

CDAP consists chiefly of these components:

- The **Router** is the only public access point into CDAP for external clients. It forwards client requests to
  the appropriate system service or application. In a secure setup, the router also performs authentication;
  it is then complemented by an authentication service that allows clients to obtain access tokens for CDAP.

- The **Master** controls and manages all services and applications.

- **System Services** provide vital platform features such datasets, transactions, service discovery logging,
  and metrics collection. System services run in application containers.

- **Application Containers** provide abstraction and isolation for execution of application code (and, as a
  special case, system services). Application containers scale linearly and elastically with the underlying
  infrastructure.

As :ref:`described above <distributed-data-application-platform>`, in a Hadoop
Environment, application containers are implemented as YARN containers and datasets use
HBase and HDFS for actual storage. In other environments, the implementation can be
different. For example, in CDAP Sandbox, all services run in a single JVM, application
containers are implemented as threads, and data is stored in the local file system.
