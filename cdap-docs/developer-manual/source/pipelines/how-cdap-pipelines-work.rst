.. meta::
    :author: Cask Data, Inc.
    :copyright: Copyright © 2016-2017 Cask Data, Inc.

.. _cdap-pipelines-how-pipelines-work:

=======================
How CDAP Pipelines Work
=======================

**A "behind-the-scenes" look at CDAP Pipelines**

CDAP Pipelines is a capability of CDAP and combines a user interface with back-end services
to enable the building, deploying, and managing of data pipelines. It has no dependencies
outside of CDAP, and all pipelines run within a Hadoop cluster.

Architecture
============
CDAP pipelines allows users to build complex data pipelines, either simple ETL
(extract-transform-load) or more complicated Data Pipelines on Hadoop. 

Data pipelines |---| unlike the linear ETL pipelines |---| are often not linear in nature
and require the performing of more complex transformations including forks and joins at
the record and feed level. They can be configured to perform various functions at
different times, including machine-learning algorithms and custom processing.

Pipelines need to support the creation of complex processing workloads that are
repeatable, high-available and easily maintainable.

Logical versus Physical Pipelines
=================================

.. figure:: _images/logical-physical-pipelines.png
 :figwidth: 50%
 :width: 3in
 :align: right
 :class: bordered-image-top-margin

 **Logical** and **Physical** Pipelines, converted by a **Planner**

.. _cdap-pipelines-how-pipelines-work-logical-start:

Within CDAP, there is the concept of *logical* and *physical* pipelines, converted by a
planner, and then run in an execution environment.

A **logical pipeline** is the view of the pipeline as seen in the CDAP Studio and the
CDAP UI. It is the view composed of sources, sinks, and other plugins, and does
not show the underlying technology used to actually manifest and run the pipeline.

This view of a pipeline focuses on the functional requirements of the pipeline, rather
than the physical runtime. It’s closer to the inherent nature of processing as viewed by a
user. This view isolates it from the volatile physical pipeline, which can be operated in
different runtime environments.

A **physical pipeline** is the manifestation of a logical pipeline as a CDAP application,
which is a collection of programs and services that read and write through the data
abstraction layer in CDAP. Physical view elements are those elements that actually run
during the execution of a data pipeline on a Hadoop cluster. They execute the MapReduce
Programs, Spark, Spark Streaming, Tigon, Workflows, and so on. The physical pipeline view
is based on the particular underlying technologies used and, as such, can be changed
dynamically.

A **planner** is responsible for converting the logical pipeline to a physical pipeline. The
planner analyzes the logical view of the pipeline and converts it to a physical execution
plan, performing optimizations, and bundling functions into one or more jobs.

.. _cdap-pipelines-how-pipelines-work-logical-end:

Execution Environment
=====================
The **execution environment** is the actual runtime environment where all the components of
the data pipeline are executed on the Hadoop cluster by CDAP. MapReduce, Spark, Spark
Streaming, Tigon are part of this environment that allows the execution of the data
pipeline. The planner maps the logical pipeline to physical pipeline using the environment
runtimes available.


Functional Components
=====================
These are the different functional components that are utilized within CDAP pipelines:

.. figure:: _images/cdap-pipelines-architecture.png
   :figwidth: 100%
   :width: 6in
   :align: center
   :class: bordered-image-top-margin

   **Functional Architecture of CDAP Pipelines**

Application
-----------
An **application** is a standardized container framework for defining all services. It is
responsible for managing the lifecycle of programs and datasets within an application.
Each CDAP pipeline is converted into a CDAP application, and deployed and managed
independently.

Application Template
--------------------
An **application template** is a user-defined, reusable, reconfigurable pattern of an
application. It is parameterized by a configuration that can be reconfigured upon
deployment. It provides a generic version of an application which can be repurposed,
instead of requiring the ongoing creation of specialized applications. The
re-configurability and modularization of the application is exposed through plugins.
CDAP provides its own, system-defined application templates, though new user-defined
ones can be added that can use the DAG interface of the CDAP Studio. The application
templates are configured using the CDAP Studio and deployed as applications into a Hadoop
cluster.

Application templates consist of a definition of its different components |---| processing,
workflow, and dataset |---| in the form of a configuration. Once a configuration is passed to
the template, a CDAP application is constructed by combining the necessary pieces to form
an executable pipeline. An application template consists of:

- A definition of the different processing supported by the template. These can include
  MapReduce, Service, Spark, Spark Streaming, Tigon, Worker, and Workflow. In the case of a
  CDAP Pipeline, it (currently) can include MapReduce, Spark, Tigon, Worker, and
  Workflow.

- A planner is optional; however, CDAP includes a planner that translates a logical
  pipeline into a physical pipeline and pieces together all of the processing components
  supported by the template.

Plugin
------
A **plugin** is a customizable module, exposed and used by an application template. It
simplifies adding new features or extending the capability of an application. Plugin
implementations are based on interfaces exposed by the application templates. Currently, CDAP
pipeline application templates expose Source, Transform, and Sink interfaces, which have
multiple implementations. Future Application Templates will expose more plugins such as
Compute, Arbitrary MR, and Spark in addition to those mentioned above.

Artifact
--------
An **artifact** is a versioned packaging format used to aggregate applications, datasets, and
plugins along with associated metadata. It is a JAR (Java Archive) containing Java classes
and resources.

CDAP Studio
-----------
**CDAP Studio** is a visual development environment for building data pipelines on
Hadoop. It has a click-and-drag interface for building and configuring data pipelines. It
also supports the ability to develop, run, automate, and operate pipelines from within
the CDAP UI. The pipeline interface integrates with the CDAP interface, allowing
drill-down debugging of pipelines and can build metrics dashboards to closely monitor
pipelines through CDAP. The CDAP Studio integrates with other capabilities such as Cask
Tracker.

Testing and Automation Framework
--------------------------------
An end-to-end **JUnit framework** (written in Java) is available in CDAP that allows
developers to test their application templates and plugins during development. It is built
as a modular framework that allows for the testing of individual components. It runs
in-memory in CDAP, as the abstracting to in-memory structures makes for easier debugging
(shorter stack traces). The tests can be integrated with continuous integration (CI) tools
such as Bamboo, Jenkins, and TeamCity.


Implementation of CDAP Pipelines
================================
CDAP pipelines are built as a CDAP capability, with three major components:

- **CDAP Studio,** the visual editor, running in a browser
- **Application Templates,** packaged as artifacts, either system- or user-defined
- **Plugins,** extensions to the application templates, in a variety of different types
  and implementations

The **CDAP Studio** interfaces with CDAP using RESTful APIs.

The **application templates** |---| ETL Batch, Data Pipeline Batch, and ETL Real-time |---| are available
by default from within the CDAP Studio. Additional application templates, such as Data Pipeline
Real-time and Spark Streaming, are being added in upcoming releases.

The ETL Batch and ETL Real-time application templates expose three plugin types: source,
transform, and sink. The Data Pipeline Batch application template exposes three additional
plugin types: aggregate, compute, and model. Additional plugin types can be created and
will be added in upcoming releases.

There are many **different plugins** that implement each of these types available
"out-of-the-box" in CDAP. New plugins can be implemented using the
public APIs exposed by the application templates. When an application template or a plugin
is deployed within CDAP, it is referred to as an **artifact**. CDAP provides capabilities to
manage the different versions of both the application templates and the plugins.

.. figure:: _images/cdap-pipelines-internals.png
   :figwidth: 100%
   :width: 6in
   :align: center
   :class: bordered-image-top-margin

   **Internals of CDAP Pipelines**


Building of a Pipeline
======================
Here is how the CDAP Studio works with CDAP to build a pipeline, beginning
with a user creating a new pipeline in the CDAP Studio. First, the components
of the CDAP Studio:

.. figure:: _images/cdap-studio-annotated.png
   :figwidth: 100%
   :width: 6in
   :align: center
   :class: bordered-image-top-margin

   **CDAP Studio, showing different UI components**

- **User Selects an Application Template**

  A user building a pipeline within the CDAP Studio will select a pipeline type, which is
  essentially picking an application template. They will pick one of ETL Batch, ETL
  Real-time, or Data Pipeline. Other application templates such as Spark Streaming will be
  available in the future.

- **Retrieve the Plugins types supported by the selected Application Template**

  Once a user has selected an application template, the Studio makes a request to
  CDAP for the different plugin types supported by the application template. In the case
  of the ETL Batch pipeline, CDAP will return Source, Transform, and Sink as plugin types.
  This allows the Studio to construct the selection drawer in the left sidebar of
  the UI.

- **Retrieve the Plugin definitions for each Plugin type**

  CDAP Studio then makes a request to CDAP for each plugin type, requesting all plugin
  implementations available for each plugin type.

- **User Builds the CDAP Pipeline**

  The user then uses the Studio's canvas to create a pipeline with the available
  plugins.

- **Validation of the CDAP Pipeline**

  The user can request at any point that the pipeline be validated. This request is
  translated into a RESTful API call to CDAP, which is then passed to the application
  template, which validates whether the pipeline is valid.

- **Application Template Configuration Generation**

  As the user is building a pipeline, the Studio is building a JSON configuration
  that, when completed, will be passed to the application template to configure and create
  an application that is deployed to the cluster.

- **Converting a logical into a physical Pipeline and registering the Application**

  When the user publishes the pipeline, the configuration generated by the Studio
  is passed to the application template as part of the creation of the Application. The
  application template takes the configuration, passes it through a planner to create a
  physical layout, appropriately generates an application specification and registers the
  specification with CDAP as an application.

- **Managing the physical Pipeline**

  Once the application is registered with CDAP, the pipeline is ready to be started. If it
  was scheduled, the schedule is ready to be enabled. The CDAP UI then uses the CDAP
  RESTful APIs to manage the pipeline's lifecycle. The pipeline can be managed from
  CDAP through the CDAP UI, by using the CDAP CLI, or by using the RESTful APIs.

- **Monitoring the physical Pipeline**

  As CDAP pipelines are run as CDAP applications, their logs and metrics are
  aggregated by the CDAP system and available using RESTful APIs.
  
