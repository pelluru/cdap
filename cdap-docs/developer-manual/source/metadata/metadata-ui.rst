.. meta::
    :author: Cask Data, Inc.
    :description: CDAP Metadata Management
    :copyright: Copyright © 2016-2017 Cask Data, Inc.

.. |tracker-sandbox-url| replace:: http://localhost:11011/tracker/ns/default
.. |literal-tracker-sandbox-url| replace:: ``http://localhost:11011/tracker/ns/default``

.. |tracker-distributed-cdap-url| replace:: http://<host>:<dashboard-bind-port>/tracker/ns/default
.. |literal-tracker-distributed-cdap-url| replace:: ``http://<host>:<dashboard-bind-port>/tracker/ns/default``


.. _metadata-ui:

================
CDAP Metadata UI
================

Introduction
============

The CDAP Metadata UI ("metadata management") lets you see how data is flowing into and out
of datasets, streams, and stream views.

It allows you to perform impact and root-cause analysis, delivers an audit-trail for
auditability and compliance, and allows you to preview data. Metadata management furnishes access to
structured information that describes, explains, and locates data, making it easier to
retrieve, use, and manage datasets.

Metadata management also allows users to update metadata for datasets and streams. Users can add,
remove, and update tags and user properties directly in the UI. It allows users to set
a preferred dictionary of tags so that teams can use the same lexicon when updating metadata.

Metadata management's UI shows a graphical visualization of the :ref:`lineage
<metadata-lineage>` of an entity. A lineage shows |---| for a specified time range
|---| all data access of the entity, and details of where that access originated from.

Metadata management also captures activity metrics for datasets. You can see the datasets that are
being used the most and view usage metrics for each dataset. This allows teams to easily
determine the appropriate dataset to use for an analysis. The metadata management meter (currently in beta)
rates each dataset on a scale that shows how active a dataset is in the system. Users can see the
datasets that are being used the most and view usage metrics for each dataset. This
allows teams to easily find the right dataset to use for analysis. The metadata management meter
also rates each dataset on a scale to quickly show you how active a dataset is in the
system.

Metadata management provides users with the ability to define a Data Dictionary that can be applied across
all datasets in a namespace. The Data Dictionary allows users to standarize column names, types,
if the column contains Personally Identifiable Information (PII) data, and a description of the column.
This can be useful for new team members, allowing them to understand the data stored in datasets quickly.

**Harvest, Index, Track, and Analyze Datasets**

- Immediate, timely, and seamless capture of technical, business, and operational metadata,
  enabling faster and better traceability of all datasets.

- Through its use of lineage, it lets you understand the impact of changing a dataset on
  other datasets, processes or queries.

- Tracks the flow of data across enterprise systems and data lakes.

- Provides viewing and updating complete metadata on datasets, enabling traceability to resolve
  data issues and to improve data quality.

- Collects usage metrics about datasets so that you know which datasets are being used most often.

- Provides the ability to designate certain tags as "preferred" so that teams can easily find and tag datasets.

- Allows users to preview data directly in the UI.

**Supports Standardization, Governance, and Compliance Needs**

- Provide IT with the traceability needed in governing datasets and in applying compliance
  rules through seamless integration with other extensions.

- Metadata management has consistent definitions of metadata-containing information about the data to
  reconcile differences in terminologies.

- It helps you in the understanding of the lineage of your business-critical data.

- The Data Dictionary allows you to standarize column names and definitions across datasets.

**Blends Metadata Analytics and Integrations**

- See how your datasets are being created, accessed, and processed.

- Extensible integrations are available with enterprise-grade MDM (master data management)
  systems such as `Cloudera Navigator <https://www.cloudera.com/products/cloudera-navigator.html>`__
  for centralizing metadata repository and the delivery of complete, accurate, and correct
  data.


Example Use Case
----------------
An example use case describes how metadata management was employed in the `data cleansing and validating of
three billion records <http://customers.cask.co/rs/882-OYR-915/images/tracker-casestudy1.pdf>`__.


Search
======
Searching in metadata management is provided by an interface similar to that of a popular search engine:

.. figure:: /_images/metadata/tracker-home-search.png
  :figwidth: 100%
  :width: 800px
  :align: center
  :class: bordered-image

In the text box, you enter your search terms:

- Multiple search terms can be searched by separating them with a space character.
- Search terms are case-insensitive.
- Search the metadata of entities by using either a complete or partial name followed by
  an asterisk ``*``, as described in the :ref:`Metadata HTTP RESTful API
  <http-restful-api-metadata-query-terms>`.
- Metadata management searches tags, properties, and schema of CDAP datasets, streams, and stream views.

For example, if you have just started CDAP and enabled metadata management, you could enter a search
term such as ``a* k*``, which will find all entities that begin with the letter ``a`` or
``k``.

The results would appear similar to this:

.. figure:: /_images/metadata/tracker-first-search.png
  :figwidth: 100%
  :width: 800px
  :align: center
  :class: bordered-image

In this example, metadata management has found two datasets that satisfied the condition. The search
used is shown in the upper-left, and the results show the datasets found with
information and links for each.

**On the left side** is the **Filter** pane, which provides information on what was found (the
entities and metadata types) with statistics of the number found for each category. A blue
checkbox allows you to filter based on these attributes. If you mouse over a category, an
``only`` link will appear, which allows you to select *only* that category as a filter.

Note that the *entities* and *metadata* filters have an ``and`` relationship; at least one
selection must be made in each of *entities* and *metadata* for there to be any results
that appear.

**On the right side** is a sortable list of results. It is sortable by one of *Create Date*, the entity
ID (name), or the metadata management score.

Each entry in the list provides a summery of information about the entity, and its name is
a hyperlink to further details: metadata, lineage, and audit log.

The **Jump** button provides three actions: go to the selected entity in CDAP, or add it
to a new CDAP pipeline as a source or as a sink. Datasets can be added as sources or
sinks to batch pipelines, while streams can be sources in batch pipelines or sinks in
real-time pipelines.

Entity Details
==============
Clicking on a name in the search results list will take you to details for a particular
entity. Details are provided on the tabs *Metadata*, *Lineage*, *Audit Log*, *Preview*
(included if the dataset is explorable), and *Usage*.

**Metadata**

The *Metadata* tab provides lists of the *System Tags*, *User Tags*, *Schema*, *User
Properties*, and *System Properties* that were found for the entity. The values shown will
vary depending on the type of entity and each individual entity. For instance, a stream
may have a schema attached, and if so, it will be displayed.

.. figure:: /_images/metadata/tracker-metadata.png
  :figwidth: 100%
  :width: 800px
  :align: center
  :class: bordered-image

You can add user tags to any entity by clicking the plus button in the UI. You can also
remove tags by hovering over the tag and clicking the x. You can also add and remove User
Properties for the dataset or stream. This is useful for storing additional details about
the dataset for others to see.

**Lineage**

The *Lineage* tab shows the relationship between an entity and the programs that are
interacting with it. As different lineage diagrams can be created for the same entity,
depending on the particular set of programs selected to construct the diagram, a green
button in the shape of an arrow is used to cycle through the different lineage digrams
that a particular entity participates in.

A date menu in the left side of the diagram lets you control the time range that the
diagram displays. By default, the last seven days are used, though a custom range can be
specified, in addition to common time ranges (two weeks to one year).

.. figure:: /_images/metadata/tracker-lineage.png
  :figwidth: 100%
  :width: 800px
  :align: center
  :class: bordered-image

**Audit Log**

The *Audit Log* tab shows each record in the *_auditLog* dataset that has been created for
that particular entity, displayed in reverse chronological order. Because of how datasets
work in CDAP, reading and writing from a flow or service to a dataset shows an access of
"UNKNOWN" rather than indicating if it was read or write access. This will be addressed in
a future release.

A date menu in the left side of the diagram lets you control the time range that the
diagram displays. By default, the last seven days are used, though a custom range can be
specified, in addition to common time ranges (two weeks to one year).

.. figure:: /_images/metadata/tracker-audit-log.png
  :figwidth: 100%
  :width: 800px
  :align: center
  :class: bordered-image

**Preview**

The *Preview* tab (if available) shows a preview for the dataset. It is available for all datasets that are
explorable. You can scroll for up to 500 records. For additional analysis, use the *Jump*
menu to go into CDAP and explore the dataset using a custom query.

.. figure:: /_images/metadata/tracker-preview.png
  :figwidth: 100%
  :width: 800px
  :align: center
  :class: bordered-image

**Usage**

The *Usage* tab shows a set of graphs displaying usage metrics for the dataset. At the top is a
histogram of all audit messages for a particular dataset. Along the bottom of the screen is a set of
charts displaying the Applications and Programs that are accessing the dataset, and a table showing
the last time a specific message was received about the dataset. Clicking the Application name or
the Program name will take you to that entity in the main CDAP UI.

.. figure:: /_images/metadata/tracker-usage.png
  :figwidth: 100%
  :width: 800px
  :align: center
  :class: bordered-image

**Preferred Tags**

The *Tags* tab at the top of the page allows you to enter a common set of preferred terms to use when
adding tags to datasets. Preferred tags show up first when adding tags, and will guide your team to
use the same terminology. Any preferred tag that has not been attached to any entities can be deleted
by clicking the red trashcan icon. If a preferred tag has been added to an entity, you cannot delete it,
but you can demote it back to just being a user tag.

.. figure:: /_images/metadata/tracker-tags.png
  :figwidth: 100%
  :width: 800px
  :align: center
  :class: bordered-image

To add preferred tags, click the **Add Preferred Tags** button and use the UI to add or import a
list of tags that you would like to be "preferred". If the tag already exists in CDAP,
it will be promoted from being a user tag to being a preferred tag. If it is a new tag
in CDAP, it will be added in the *Preferred Tags* list.

.. figure:: /_images/metadata/tracker-tags-upload.png
  :figwidth: 100%
  :width: 800px
  :align: center
  :class: bordered-image

**Data Dictionary**

The *Dictionary* tab at the top of the page allows you to add a set of columns and descriptions that
can be viewed by anyone in the namespace. This allows you to provide more detailed descriptions about
columns as well as the preferred naming convention, type, and whether the column contains personally
identifying information (PII) or not. These definitions will be applied to all datasets in the namespace.
For example, any dataset containing the column ``customerId`` will have the same definition and type.

.. figure:: /_images/metadata/tracker-dictionary.png
  :figwidth: 100%
  :width: 800px
  :align: center
  :class: bordered-image

Integrations
============
Metadata management allows for an easy integration with `Cloudera Navigator
<https://www.cloudera.com/products/cloudera-navigator.html>`__  by providing a UI to
connecting to a Navigator instance:

.. figure:: /_images/metadata/tracker-integration-configuration.png
  :figwidth: 100%
  :width: 800px
  :align: center
  :class: bordered-image

Details on completing this form are described in CDAP's documentation on the
:ref:`Navigator Integration Application <navigator-integration>`.


Administrating Metadata Management
==================================
CDAP metadata management consists of an application in CDAP with two programs and six datasets:

- ``_Tracker`` application: names begins with an underscore
- ``TrackerService``: Service exposing the metadata management API endpoints
- ``AuditLogFlow``: Flow that subscribes to Kafka audit messages and stores them in the
  ``_auditLog`` dataset
- ``_auditLog``: Custom dataset for storing audit messages
- ``_auditMetrics``: Custom cube dataset for collecting dataset metrics
- ``_auditTagsTable``: Custom dataset for storing preferred tags
- ``_timeSinceTable``: Custom dataset for storing the last time a specific audit
  message was received
- ``_dataDictionary``: A Table dataset containing the columns and definitions of the Data Dictionary
- ``_configurationTable``: A Key-value table containing metadata management configuration options

The metadata management UI is shipped with CDAP, started automatically in CDAP Sandbox as part of the
CDAP UI. It is available at:

  |literal-tracker-sandbox-url|

or (Distributed CDAP):

  |literal-tracker-distributed-cdap-url|

The application is built from a system artifact included with CDAP, |literal-cdap-metadata-management-version-jar|.

To administer metadata management, an :ref:`HTTP RESTful API <http-restful-api-metadata-management>` is available.

.. highlight:: xml

Installation
------------
The CDAP Metadata Management Application is deployed from its system artifact included
with CDAP. A CDAP administrator does not need to build anything to add metadata management
to CDAP; they merely need to enable the application after starting CDAP.

Enabling Metadata Management
----------------------------
Metadata management is enabled automatically in CDAP Sandbox and the UI is available at |tracker-sandbox-url|.
In the Distributed version of CDAP, you must manually enable metadata management in each namespace by visiting
|literal-tracker-distributed-cdap-url| and pressing the ``"Enable"`` button.

Once pressed, the application will be deployed, the datasets created (if necessary), the
flow and service started, and search and audit logging will become available.

If you are enabling metadata management from outside the UI, you will need to follow these steps:

- Using the CDAP CLI, load the artifact (|literal-cdap-metadata-management-version-jar|):

  .. container:: highlight

    .. parsed-literal::

      |cdap >| load artifact target/|cdap-metadata-management-version-jar|

.. highlight:: json

- Create an application configuration file (``appconfig.txt``) that contains the
  Audit Log reader configuration (the property ``auditLogConfig``). For example::

    {
      "config": {
        "auditLogConfig" : {
          "topic" : "<audit.topic>",
          "zookeeperString" : "<zookeeper.quorum>"
        }
      }
    }

  substituting for ``<audit.topic>`` and ``<zookeeper.quorum>`` with appropriate values from ``cdap-site.xml``.

- Create a CDAP application using the configuration file:

  .. container:: highlight

    .. parsed-literal::

      |cdap >| create app TrackerApp tracker |cdap-metadata-management-version| USER

Restarting CDAP
---------------
As metadata management is an application running inside CDAP, it does not start up automatically when
CDAP is restarted. Each time that you start CDAP, you will need to re-enable metadata management.
Re-enabling metadata management does not recreate the datasets; instead, the same datasets as were
used in previous runs are used.

If you are using the audit log feature of metadata management, it is best that metadata management be enabled
**before** you begin any other applications.

If the installation of CDAP is an upgrade from a previous version, all activity and
datasets prior to the enabling of metadata management will not be available or seen in the CDAP UI.

Disabling and Removing Metadata Management
------------------------------------------
If for some reason you need to disable or remove metadata management, you would need to:

- stop all programs of the ``_Tracker`` application
- delete the metadata management application
- delete the metadata management datasets

