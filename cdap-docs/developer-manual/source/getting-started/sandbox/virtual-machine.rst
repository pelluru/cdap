.. meta::
    :author: Cask Data, Inc.
    :description: CDAP Virtual Machine
    :copyright: Copyright © 2014-2017 Cask Data, Inc.

=====================
Virtual Machine Image
=====================

.. index:: VM, Virtual, Machine

.. highlight:: console

To use the **Virtual Machine image**:

- Download and install either `Oracle VirtualBox <https://www.virtualbox.org>`__ or
  `VMWare <http://www.vmware.com/products/player>`__ player to your environment.
- Download the CDAP Sandbox Virtual Machine (*Sandbox VM*) at `<http://cask.co/downloads/#cdap>`__.
- Import the downloaded ``.ova`` file into either the VirtualBox or VMWare Player.

The CDAP Sandbox Virtual Machine is configured with the recommended settings for CDAP Sandbox:

- 4 GB of RAM
- Ubuntu Desktop Linux
- 40 GB of virtual disk space

It has pre-installed all the software that you need to run and develop CDAP applications:

- The CDAP Sandbox is installed under ``/opt/cdap/sdk``
  and will automatically start when the virtual machine starts.
- A Java JDK is installed.
- Maven is installed and configured to work for CDAP.
- Both IntelliJ IDEA and Eclipse IDE are installed and available through desktop links once the
  virtual machine has started.
- Links on the desktop are provided to the CDAP Sandbox, CDAP UI, CDAP Examples, and CDAP documentation.
- The Chromium web browser is included. The default page for the CDAP UI, available through a desktop link, is
  :cdap-ui:`http://localhost:11011/ <>`.

No password is required to enter the virtual machine; however, should you need to install or
remove software, the admin user and password are both ``cdap``.

.. include:: ../dev-env.rst
   :start-line: 7
   :end-line: 22

.. ifconfig:: snapshot_version

  .. tabbed-parsed-literal::
    :tabs: "Linux Virtual Machine"
    :independent:

    $ mvn archetype:generate \
        -DarchetypeGroupId=co.cask.cdap \
        -DarchetypeArtifactId=cdap-app-archetype \
        |archetype-repository| \
        |archetype-version| \
        -DartifactId=myExampleApp \
        -DgroupId=org.example.app

.. ifconfig:: not snapshot_version

  .. tabbed-parsed-literal::
    :tabs: "Linux Virtual Machine"
    :independent:

    $ mvn archetype:generate \
        -DarchetypeGroupId=co.cask.cdap \
        -DarchetypeArtifactId=cdap-app-archetype \
        |archetype-version| \
        -DartifactId=myExampleApp \
        -DgroupId=org.example.app

.. include:: ../dev-env.rst
   :start-line: 46

Starting and Stopping CDAP Sandbox
========================================

Use the ``cdap`` script (located in ``/opt/cdap/sdk/bin``) to start and stop the CDAP Sandbox:

.. tabbed-parsed-literal::
   :tabs: "Linux Virtual Machine"
   :independent:

   $ /opt/cdap/sdk/bin/cdap sandbox start
     . . .
   $ /opt/cdap/sdk/bin/cdap sandbox stop

Note that starting CDAP is not necessary if you use the Virtual Machine, as it
starts the CDAP Sandbox automatically on startup.

Once CDAP is started successfully, in a web browser you will be able to see the CDAP
UI running at :cdap-ui:`http://localhost:11011/ <>`, where you can deploy example applications and
interact with CDAP.

.. include:: /_includes/building-apps.txt
