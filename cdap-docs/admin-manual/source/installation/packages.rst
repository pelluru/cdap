.. meta::
    :author: Cask Data, Inc.
    :copyright: Copyright © 2014-2017 Cask Data, Inc.

:section-numbering: true

.. _admin-installation-packages:

==================================
Manual Installation using Packages
==================================

.. include:: ../_includes/installation/installation-steps-images.txt

.. rubric:: Notes

This section describes installing CDAP on Hadoop clusters that are:

- Generic Apache Hadoop distributions;
- CDH (Cloudera Distribution of Apache Hadoop) clusters *not managed* with Cloudera Manager; or
- HDP (Hortonworks Data Platform) clusters *not managed* with Apache Ambari.

Cloudera Manager (CDH), Apache Ambari (HDP), and MapR distributions should be installed
with our other :ref:`distribution instructions <installation-index>`.

- As CDAP depends on HDFS, YARN, HBase, ZooKeeper, and (optionally) Hive and Spark, it must be installed
  on cluster host(s) with full client configurations for these dependent services.

- The CDAP Master Service must be co-located on a cluster host with an HDFS client, a YARN
  client, an HBase client, and |---| optionally |---| Hive or Spark clients.

- Note that these clients are redundant if you are co-locating the CDAP Master
  on a cluster host (or hosts, in the case of a deployment with high availability) with
  actual services, such as the HDFS NameNode, the YARN resource manager, or the HBase
  Master.

- You can download the `Hadoop client <http://hadoop.apache.org/releases.html#Download>`__
  and `HBase client <http://www.apache.org/dyn/closer.cgi/hbase/>`__ libraries, and then
  install them on the hosts running CDAP services. No Hadoop or HBase services need be running.

- All services run as the ``cdap`` user installed by the package manager. See `Create the "cdap" User`_ below.

- If you are installing CDAP with the intention of using *replication,* see these
  instructions on :ref:`CDAP Replication <installation-replication>` *before* installing or starting CDAP.


Preparing the Cluster
=============================
Please review the :ref:`Software Prerequisites <admin-manual-software-requirements>`,
as a configured Hadoop, HBase, and Hive (plus an optional Spark client) needs to be configured on the
node(s) where CDAP will run.

.. Hadoop Configuration
.. --------------------
.. include:: ../_includes/installation/hadoop-configuration.txt

Create the "cdap" User
----------------------

.. highlight:: console

The base CDAP package will install ``cdap`` as a system user. However, in order to have a
consistent UID for the ``cdap`` user across the cluster, or to ensure that it is created
on the namenode when installing CDAP elsewhere, you need to check that the ``cdap`` user
is installed |---| and installed consistently |---| on all nodes of the cluster.
Though the base CDAP package will automatically install the ``cdap`` system user, it's
best if the ``cdap`` user is manually created on all nodes of the cluster beforehand.

To prepare your cluster for CDAP, manually create a ``cdap`` user on all nodes of the
cluster. Make sure that the UID and GID for the ``cdap`` user is the same on each node of
the cluster::

  $ id cdap
  uid=503(cdap) gid=504(cdap) groups=504(cdap)

*Note:* The values returned by ``id cdap`` may differ from these shown, depending on your
system.

When installing CDAP on an edge node, the ``cdap`` system user is only created locally. As
Hadoop resolves users at the NameNode, the ``cdap`` user must also be added there, or name
resolution for the user will fail.

.. HDFS Permissions
.. ----------------
.. include:: /../target/_includes/packages-hdfs-permissions.rst


Downloading and Distributing Packages
=====================================

Preparing Package Managers
--------------------------

.. include:: /../target/_includes/packages-installation.rst
    :start-after: .. _packages-preparing-package-managers:
    :end-before: .. _packages-package-installation-title:


Installing CDAP Services
========================

.. include:: /../target/_includes/packages-installation.rst
    :start-after: .. _packages-package-installation-title:


.. |display-distribution| replace:: Package Managers (RPM/Debian)

.. |hdfs-user| replace:: ``yarn``

.. |su_hdfs| replace:: su hdfs

.. include:: /../target/_includes/packages-configuration.rst
    :end-before: .. configuration-enabling-kerberos:


.. Starting CDAP Services
.. ======================

.. include:: /../target/_includes/packages-starting.rst


.. _packages-verification:

Verification
============

.. include:: /_includes/installation/smoke-test-cdap.txt


.. _packages-installation-advanced-topics:

Advanced Topics
===============

- :ref:`Enabling Security <packages-configuration-security>`
- :ref:`Enabling Kerberos <packages-configuration-enabling-kerberos>`
- :ref:`Enabling CDAP High Availability <packages-configuration-highly-available>`
- :ref:`Enabling Hive Execution Engines <packages-configuration-enabling-hive-execution-engines>`

.. _packages-configuration-security:

.. Enabling Perimeter Security
.. ---------------------------
.. include:: /../target/_includes/packages-configuration.rst
    :start-after: .. _packages-configuration-eps:

.. _packages-configuration-enabling-kerberos:

.. Enabling Kerberos
.. -----------------
.. include:: /../target/_includes/packages-configuration.rst
    :start-after: .. configuration-enabling-kerberos:
    :end-before: .. _packages-configuration-eps:

.. Enabling CDAP HA
.. ----------------
.. include:: /../target/_includes/packages-ha-installation.rst

.. Enabling Hive Execution Engines
.. -------------------------------
.. _packages-configuration-enabling-hive-execution-engines:

.. include:: /_includes/installation/hive-execution-engines.txt
