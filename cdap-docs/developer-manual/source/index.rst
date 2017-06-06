.. meta::
    :author: Cask Data, Inc.
    :description: Introduction to the Cask Data Application Platform
    :copyright: Copyright © 2014-2017 Cask Data, Inc.

.. _developer-index:

=====================
CDAP Developer Manual
=====================

.. |getting-started| replace:: **Getting Started Developing:**
.. _getting-started: getting-started/index.html

- |getting-started|_ **A quick, hands-on introduction to developing with CDAP,**  which guides you through
  installing the CDAP Sandbox, setting up your development environment, starting and stopping CDAP,
  and building and running example applications.


.. |overview| replace:: **Overview:**
.. _overview: overview/index.html

- |overview|_ Covers the **overall architecture and technology behind CDAP,** including
  the abstraction of *Data* and *Applications*, CDAP modes and components, and the anatomy
  of a Big Data application.


.. |building-blocks| replace:: **Building Blocks:**
.. _building-blocks: building-blocks/index.html

- |building-blocks|_ This section covers the **two core abstractions** in the Cask Data
  Application Platform: **Data** and **Applications**. *Data* abstractions include *streams*,
  *datasets*, and *views*. *Application* abstraction is accomplished using *flows* and *flowlets*, *MapReduce*, *Spark*,
  *workers*, *workflows*, *schedules*, and *services*. Details are provided on working with these abstractions to
  build Big Data applications.


.. |metadata| replace:: **Metadata:**
.. _metadata: metadata/index.html

- |metadata|_ A CDAP capability that automatically captures *metadata* and lets you see
  **how data is flowing** into and out of datasets, streams, and stream views.
  :ref:`Audit logging <audit-logging>` provides a chronological ledger containing evidence
  of operations or changes on CDAP entities.


.. |pipelines| replace:: **Pipelines:**
.. _pipelines: pipelines/index.html

- |pipelines|_ A capability of CDAP that combines a user interface with back-end services
  to enable the **building, deploying, and managing of data pipelines.**


.. |security| replace:: **Security:**
.. _security: security/index.html

- |security|_ CDAP supports securing clusters using **perimeter security. Configuration
  and client authentication** are covered in this section.


.. |testing| replace:: **Testing and Debugging:**
.. _testing: testing/index.html

- |testing|_ CDAP has a **test framework** that developers can use with their applications
  plus **tools and practices for debugging** your application prior to deployment.


.. |ingesting-tools| replace:: **Ingesting Data:**
.. _ingesting-tools: ingesting-tools/index.html

- |ingesting-tools|_ CDAP comes with a number of tools to make a developer’s life easier. These
  tools help with **ingesting data into CDAP** using Java, Python, and Ruby APIs,
  and include an Apache Flume Sink implementation.


.. |data-exploration| replace:: **Data Exploration:**
.. _data-exploration: data-exploration/index.html

- |data-exploration|_ Data in CDAP can be **explored without writing any code** through the use of **ad-hoc SQL-like queries**.
  Exploration of streams and datasets, along with integration with business intelligence tools, are covered in this section.


.. |advanced| replace:: **Advanced Topics:**
.. _advanced: advanced/index.html

- |advanced|_ Covers **advanced topics on CDAP** that will be of interest to
  developers who want a deeper dive into CDAP, including **adding a custom logback** to a
  CDAP application, suggested **best practices for CDAP development**, **class loading in
  CDAP**, and on **configuring program resources** and **program retry policies** of a CDAP application.
