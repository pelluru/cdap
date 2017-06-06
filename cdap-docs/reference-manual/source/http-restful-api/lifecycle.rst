.. meta::
    :author: Cask Data, Inc.
    :description: HTTP RESTful Interface to the Cask Data Application Platform
    :copyright: Copyright © 2014-2017 Cask Data, Inc.

.. _http-restful-api-lifecycle:

==========================
Lifecycle HTTP RESTful API
==========================

.. highlight:: console

Use the CDAP Lifecycle HTTP RESTful API to deploy or delete applications and manage the lifecycle of
flows, MapReduce and Spark programs, custom services, workers, and workflows.

Details of these CDAP components can be found in the :ref:`Developer Manual: Building Blocks <building-blocks>`.

.. Base URL explanation
.. --------------------
.. include:: base-url.txt


Application Lifecycle
=====================

.. _http-restful-api-lifecycle-create-app:

Create an Application
---------------------
To create an application, submit an HTTP PUT request::

  PUT /v3/namespaces/<namespace-id>/apps/<app-id>

To create an application with a non-default version, submit an HTTP POST request with the version specified::

  POST /v3/namespaces/<namespace-id>/apps/<app-id>/versions/<version-id>/create

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description
   * - ``namespace-id``
     - Namespace ID
   * - ``app-id``
     - Name of the application
   * - ``version-id``
     - Version of the application, typically following `semantic versioning
       <http://semver.org>`__; versions ending with ``-SNAPSHOT`` can be overwritten

The request body is a JSON object specifying the artifact to use to create the application,
and an optional application configuration. For example:

.. container:: highlight

  .. parsed-literal::
    |$| PUT /v3/namespaces/default/apps/purchaseWordCount -d
    {
      "artifact": {
        "name": "WordCount",
        "version": "|release|",
        "scope": "user"
      },
      "config": {
        "stream": "purchaseStream"
      },
      "principal":"user/example.net@EXAMPLEKDC.NET",
      "app.deploy.update.schedules":"true"
    }

will create an application named ``purchaseWordCount`` from the example ``WordCount`` artifact.
The application will receive the specified ``config``, which will configure the application
to create a stream named ``purchaseStream`` instead of using the default stream name.

Optionally, you can specify a Kerberos principal with which the application should be deployed.
If a Kerberos principal is specified, then all the streams and datasets created by the
application will be created with the application's Kerberos principal.

Optionally, you can set or reset the flag ``app.deploy.update.schedules``. If true,
redeploying an application will modify any schedules that currently exist for the application;
if false, redeploying an application does not create any new schedules and existing schedules
are neither deleted nor updated.

Update an Application
---------------------
To update an application, submit an HTTP POST request::

  POST /v3/namespaces/<namespace-id>/apps/<app-id>/update

The request body is a JSON object specifying the updated artifact version and the updated application
config. For example, a request body of:

.. container:: highlight

  .. parsed-literal::
    |$| POST /v3/namespaces/default/apps/purchaseWordCount/update -d
    {
      "artifact": {
        "name": "WordCount",
        "version": "|release|",
        "scope": "user"
      },
      "config": {
        "stream": "logStream";
      },
      "principal":"user/example.net@EXAMPLEKDC.NET"
    }

will update the ``purchaseWordCount`` application to use version |release| of the ``WordCount`` artifact,
and update the name of the stream to ``logStream``. If no artifact is given, the current artifact will be
used.

Only changes to artifact version are supported; changes to the artifact name are not allowed. If no
``config`` is given, the current ``config`` will be used. If the ``config`` key is present, the current
``config`` will be overwritten by the ``config`` specified in the request. As the principal of an
application cannot be updated, during an update the principal should either be the same or absent.

Deploy an Artifact and Application
----------------------------------
To deploy an application from your local file system into the namespace *namespace-id*,
submit an HTTP POST request::

  POST /v3/namespaces/<namespace-id>/apps

with the name of the JAR file as a header::

  X-Archive-Name: <JAR filename>

and Kerberos principal with which the application is to be deployed (if required)::

  X-Principal: <Kerberos Principal>

and enable or disable updating schedules of the existing workflows using the header::

  X-App-Deploy-Update-Schedules: <Update Schedules>

This will add the JAR file as an artifact and then create an application from that artifact.
The archive name must be in the form ``<artifact-name>-<artifact-version>.jar``.
An optional header can supply a configuration object as a serialized JSON string::

  X-App-Config: <JSON Serialization String of the Configuration Object>

The application's content is the body of the request::

  <JAR binary content>

Invoke the same command to update an application to a newer version.
However, be sure to stop all of its flows, Spark and MapReduce programs before updating the application.

.. highlight:: java

For an application that has a configuration class such as::

  public static class MyAppConfig extends Config {
    String streamName;
    String datasetName;
  }

.. highlight:: console

We can deploy it with this RESTful call::

  POST /v3/namespaces/<namespace-id>/apps
  -H "X-Archive-Name: <jar-name>" \
  -H "X-Principal: <kerberos-principal>" \
  -H "X-App-Deploy-Update-Schedules: true" \
  -H 'X-App-Config: "{\"streamName\" : \"newStream\", \"datasetName\" : \"newDataset\" }" ' \
  --data-binary "@<jar-location>"

Note that the ``X-App-Config`` header contains the JSON serialization string of the ``MyAppConfig`` object.

Deployed Applications
---------------------

To list all of the deployed applications in the namespace *namespace-id*, issue an HTTP
GET request::

  GET /v3/namespaces/<namespace-id>/apps[?artifactName=<artifact-names>[&artifactVersion=<artifact-version>]]

This will return a JSON String map that lists each application with its name, description, and artifact.
The list can optionally be filtered by one or more artifact names. It can also be filtered by artifact version.
For example::

  GET /v3/namespaces/<namespace-id>/apps?artifactName=cdap-data-pipeline,cdap-etl-realtime

will return all applications that use either the ``cdap-data-pipeline`` or ``cdap-etl-realtime`` artifacts.

.. _http-restful-api-lifecycle-app-deployed-details:

Details of a Deployed Application
---------------------------------

For detailed information on an application that has been deployed in the namespace
*namespace-id*, use::

  GET /v3/namespaces/<namespace-id>/apps/<app-id>

The information will be returned in the body of the response. It includes the name and description
of the application; the artifact, streams, and datasets that it uses, all of its programs; and the
Kerberos principal, if that was provided during the deployment.

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description
   * - ``namespace-id``
     - Namespace ID
   * - ``app-id``
     - Name of the application

.. rubric:: HTTP Responses

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Status Codes
     - Description
   * - ``200 OK``
     - The event successfully called the method, and the body contains the results

Listing Versions of an Application
----------------------------------

To list all the versions of an application, submit an HTTP GET::

  GET /v3/namespaces/<namespace-id>/apps/<app-id>/versions

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description
   * - ``namespace-id``
     - Namespace ID
   * - ``app-id``
     - Name of the application being called

The response will be a JSON array containing details about the application. The details
returned depend on the application.

For example, depending on the versions deployed::

  GET /v3/namespaces/default/apps/HelloWorld/versions

.. highlight:: json

could return in a JSON array a list of the versions of the application::

  ["1.0.1", "2.0.3"]

.. highlight:: console

Delete an Application
---------------------
To delete an application |---| together with all of its flows, MapReduce or Spark
programs, schedules, custom services, and workflows |---| submit an HTTP DELETE::

  DELETE /v3/namespaces/<namespace-id>/apps/<app-id>

To delete a specific version of an application, submit an HTTP DELETE that includes the version::

  DELETE /v3/namespaces/<namespace-id>/apps/<app-id>/versions/<version-id>

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description
   * - ``namespace-id``
     - Namespace ID
   * - ``app-id``
     - Name of the application to be deleted
   * - ``version-id``
     - Version of the application to be deleted

**Note:** The ``app-id`` in this URL is the name of the application as
configured by the application specification, and not necessarily the same as the name of
the JAR file that was used to deploy the application.

This does not delete the streams and datasets associated with the application because they
belong to the namespace, not the application. Also, this does not delete the artifact used
to create the application.

.. _http-restful-api-program-lifecycle:

Program Lifecycle
=================

Details of a Program
--------------------
After an application is deployed, you can retrieve the details of its flows, MapReduce and Spark programs,
custom services, schedules, workers, and workflows by submitting an HTTP GET request::

  GET /v3/namespaces/<namespace-id>/apps/<app-id>/<program-type>/<program-id>

To retrieve information about the schedules of the program's workflows, use::

  GET /v3/namespaces/<namespace-id>/apps/<app-id>/workflows/<workflow-id>/schedules

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description
   * - ``namespace-id``
     - Namespace ID
   * - ``app-id``
     - Name of the application being called
   * - ``program-type``
     - One of ``flows``, ``mapreduce``, ``services``, ``spark``, ``workers``, or ``workflows``
   * - ``program-id``
     - Name of the *flow*, *MapReduce*, *custom service*, *Spark*, *worker*, or *workflow*
       being called
   * - ``workflow-id``
     - Name of the *workflow* being called, when retrieving schedules

The response will be a JSON array containing details about the program. The details returned depend on the
program type.

For example::

  GET /v3/namespaces/default/apps/HelloWorld/flows/WhoFlow

.. highlight:: json-ellipsis

will return in a JSON array information about the *WhoFlow* of the application *HelloWorld*. The results will
be similar to this (pretty-printed and portions deleted to fit)::

  {
      "className": "co.cask.cdap.examples.helloworld.HelloWorld$WhoFlow",
      "name": "WhoFlow",
      "description": "A flow that collects names",
      "flowlets": {
          "saver": {
              "flowletSpec": {
                  "className": "co.cask.cdap.examples.helloworld.HelloWorld$NameSaver",
                  "name": "saver",
                  "description": "",
                  "failurePolicy": "RETRY",
                  "dataSets": [
                      "whom"
                  ],
                  "properties": {
                  },
                  "resources": {
                      "virtualCores": 1,
                      "memoryMB": 512
                  }
              },
              "streams": {
              },
              "datasetModules": {
              },
              "datasetSpecs": {
              },
              "instances": 1,
              "datasets": [
                  "whom"
              ],
              "inputs": {

                ...

              },
              "outputs": {
              }
          }
      },
      "connections": [
          {
              "sourceType": "STREAM",
              "sourceName": "who",
              "targetName": "saver"
          }
      ]
  }

.. highlight:: console

.. _http-restful-api-lifecycle-start:

Start a Program
---------------
After an application is deployed, you can start its flows, MapReduce and Spark programs,
custom services, workers, or workflows by submitting an HTTP POST request::

  POST /v3/namespaces/<namespace-id>/apps/<app-id>/<program-type>/<program-id>/start

You can start a program of a particular version of the application by submitting an HTTP
POST request that includes the version::

  POST /v3/namespaces/<namespace-id>/apps/<app-id>/versions/<version-id>/<program-type>/<program-id>/start

Note: Concurrent runs of flows and workers across multiple versions of the same
application are not allowed.

When starting an program, you can optionally specify runtime arguments as a JSON map in
the request body. CDAP will use these runtime arguments only for this single invocation of
the program.

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description
   * - ``namespace-id``
     - Namespace ID
   * - ``app-id``
     - Name of the application being called
   * - ``version-id``
     - Version of the application being called
   * - ``program-type``
     - One of ``flows``, ``mapreduce``, ``services``, ``spark``, ``workers``, or ``workflows``
   * - ``program-id``
     - Name of the *flow*, *MapReduce*, *custom service*, *Spark*, *worker*, or *workflow*
       being called

*Flow*, *Service*, *Spark*, and *Worker* programs do not allow concurrent program runs.
Programs of these types cannot be started unless the program is in the *STOPPED* state.
*MapReduce* and *Workflow* programs support concurrent runs. If you start one of these programs,
a new run will be started even if other runs of the program have not finished yet.

For example::

  POST /v3/namespaces/default/apps/HelloWorld/flows/WhoFlow/start -d '{ "foo":"bar", "this":"that" }'

will start the *WhoFlow* flow in the *HelloWorld* application with two runtime arguments.

.. _http-restful-api-lifecycle-start-multiple:

Start Multiple Programs
-----------------------
You can start multiple programs from different applications and program types
by submitting an HTTP POST request::

  POST /v3/namespaces/<namespace-id>/start

with a JSON array in the request body consisting of multiple JSON objects with these parameters:

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description
   * - ``appId``
     - Name of the application being called
   * - ``programType``
     - One of ``flow``, ``mapreduce``, ``service``, ``spark``, ``worker``, or ``workflow``
   * - ``programId``
     - Name of the *flow*, *MapReduce*, *custom service*, *Spark*, *worker*, or *workflow*
       being started
   * - ``runtimeargs``
     - Optional JSON object containing a string to string mapping of runtime arguments to start the program with

The response will be a JSON array containing a JSON object for each object in the input.
Each JSON object will contain these parameters:

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description
   * - ``appId``
     - Name of the application being called
   * - ``programType``
     - One of ``flow``, ``mapreduce``, ``service``, ``spark``, ``worker``, or ``workflow``
   * - ``programId``
     - Name of the *flow*, *MapReduce*, *custom service*, *Spark*, *worker*, or *workflow*
       being started
   * - ``statusCode``
     - The status code from starting an individual JSON object
   * - ``error``
     - If an error, a description of why the program could not be started (for example,
       the specified program was not found)

For example::

  POST /v3/namespaces/default/start -d '
    [
      {"appId": "App1", "programType": "Service", "programId": "Service1"},
      {"appId": "App1", "programType": "Mapreduce", "programId": "MapReduce2"},
      {"appId": "App2", "programType": "Flow", "programId": "Flow1", "runtimeargs": { "arg1":"val1" }}
    ]'

.. highlight:: json-ellipsis

will attempt to start the three programs listed in the request body. It will return a response such as::

  [
    {"appId": "App1", "programType": "Service", "programId": "Service1", "statusCode": 200},
    {"appId": "App1", "programType": "Mapreduce", "programId": "Mapreduce2", "statusCode": 200},
    {"appId": "App2", "programType":"Flow", "programId":"Flow1", "statusCode":404, "error": "App: App2 not found"}
  ]

.. highlight:: console

In this particular example, the service and mapreduce programs in the *App1* application were successfully
started, and there was an error starting the last program because the *App2* application does not exist.

.. _http-restful-api-lifecycle-stop:

Stop a Program
--------------
You can stop the flows, MapReduce and Spark programs, custom services, workers, and
workflows of an application by submitting an HTTP POST request::

  POST /v3/namespaces/<namespace-id>/apps/<app-id>/<program-type>/<program-id>/stop

You can stop the programs of a particular application version by submitting an HTTP POST
request that includes the version::

  POST /v3/namespaces/<namespace-id>/apps/<app-id>/versions/<version-id>/<program-type>/<program-id>/stop

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description
   * - ``namespace-id``
     - Namespace ID
   * - ``app-id``
     - Name of the application being called
   * - ``version-id``
     - Version of the application being called
   * - ``program-type``
     - One of ``flows``, ``mapreduce``, ``services``, ``spark``, ``workers``, or ``workflows``
   * - ``program-id``
     - Name of the *flow*, *MapReduce*, *custom service*, *Spark*, *worker*, or *workflow*
       being stopped

A program that is in the STOPPED state cannot be stopped. If there are multiple runs of the program
in the RUNNING state, this call will stop one of the runs, but not all of the runs.

For example::

  POST /v3/namespaces/default/apps/HelloWorld/flows/WhoFlow/stop

will stop the *WhoFlow* flow in the *HelloWorld* application.


.. _http-restful-api-lifecycle-stop-run:

Stop a Program Run
------------------
You can stop a specific run of a program by submitting an HTTP POST request::

  POST /v3/namespaces/<namespace-id>/apps/<app-id>/<program-type>/<program-id>/runs/<run-id>/stop

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description
   * - ``namespace-id``
     - Namespace ID
   * - ``app-id``
     - Name of the application being called
   * - ``program-type``
     - One of ``flows``, ``mapreduce``, ``services``, ``spark``, ``workers``, or ``workflows``
   * - ``program-id``
     - Name of the *flow*, *MapReduce*, *custom service*, *Spark*, *worker*, or *workflow*
       being called
   * - ``run-id``
     - Run id of the run being called

For example::

  POST /v3/namespaces/default/apps/PurchaseHistory/mapreduce/PurchaseHistoryBuilder/runs/631bc459-a9dd-4218-9ea0-d46fb1991f82/stop

will stop a specific run of the *PurchaseHistoryBuilder* MapReduce program in the *PurchaseHistory* application.

.. _http-restful-api-lifecycle-stop-multiple:

Stop Multiple Programs
----------------------
You can stop multiple programs from different applications and program types
by submitting an HTTP POST request::

  POST /v3/namespaces/<namespace-id>/stop

with a JSON array in the request body consisting of multiple JSON objects with these parameters:

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description
   * - ``appId``
     - Name of the application being called
   * - ``programType``
     - One of ``flow``, ``mapreduce``, ``service``, ``spark``, ``worker``, or ``workflow``
   * - ``programId``
     - Name of the *flow*, *MapReduce*, *custom service*, *Spark*, *worker*, or *workflow*
       being stopped

The response will be a JSON array containing a JSON object corresponding to each object in the input.
Each JSON object will contain these parameters:

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description
   * - ``appId``
     - Name of the application being called
   * - ``programType``
     - One of ``flow``, ``mapreduce``, ``service``, ``spark``, ``worker``, or ``workflow``
   * - ``programId``
     - Name of the *flow*, *MapReduce*, *custom service*, *Spark*, *worker*, or *workflow*
       being stopped
   * - ``statusCode``
     - The status code from stopping an individual JSON object
   * - ``error``
     - If an error, a description of why the program could not be stopped (for example,
       the specified program was not found)

For example::

  POST /v3/namespaces/default/stop -d '
    [
      {"appId": "App1", "programType": "Service", "programId": "Service1"},
      {"appId": "App1", "programType": "Mapreduce", "programId": "MapReduce2"},
      {"appId": "App2", "programType": "Flow", "programId": "Flow1"}
    ]'

.. highlight:: json-ellipsis

will attempt to stop the three programs listed in the request body. It will return a response such as::

  [
    {"appId": "App1", "programType": "Service", "programId": "Service1", "statusCode": 200},
    {"appId": "App1", "programType": "Mapreduce", "programId": "Mapreduce2", "statusCode": 200},
    {"appId": "App2", "programType":"Flow", "programId":"Flow1", "statusCode":404, "error": "App: App2 not found"}
  ]

.. highlight:: console

In this particular example, the service and mapreduce programs in the *App1* application were successfully
stopped, and there was an error starting the last program because the *App2* application does not exist.

.. _http-restful-api-lifecycle-status:

Status of a Program
-------------------
To retrieve the status of a program, submit an HTTP GET request::

  GET /v3/namespaces/<namespace-id>/apps/<app-id>/<program-type>/<program-id>/status

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description
   * - ``namespace-id``
     - Namespace ID
   * - ``app-id``
     - Name of the application being called
   * - ``program-type``
     - One of ``flows``, ``mapreduce``, ``schedules``, ``services``, ``spark``, ``workers``, or ``workflows``
   * - ``program-id``
     - Name of the *flow*, *MapReduce*, *schedule*, *custom service*, *Spark*, *worker*, or *workflow*
       being called

The response will be a JSON array with status of the program. For example, retrieving the status of the
*WhoFlow* of the program *HelloWorld*::

  GET /v3/namespaces/default/apps/HelloWorld/flows/WhoFlow/status

.. highlight:: json-ellipsis

will return (pretty-printed) a response such as::

  {
      "status": "STOPPED"
  }

.. highlight:: console

.. _http-restful-api-lifecycle-status-multi:

Status of Multiple Programs
---------------------------
You can retrieve the status of multiple programs from different applications and program types
by submitting an HTTP POST request::

  POST /v3/namespaces/<namespace-id>/status

with a JSON array in the request body consisting of multiple JSON objects with these parameters:

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description
   * - ``appId``
     - Name of the application being called
   * - ``programType``
     - One of ``flow``, ``mapreduce``, ``schedule``, ``service``, ``spark``, ``worker``, or ``workflow``
   * - ``programId``
     - Name of the *flow*, *MapReduce*, *schedule*, *custom service*, *Spark*, *worker*, or *workflow*
       being called

The response will be the same JSON array as submitted with additional parameters for each
of the underlying JSON objects:

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description
   * - ``status``
     - Maps to the status of an individual JSON object's queried program
       if the query is valid and the program was found
   * - ``statusCode``
     - The status code from retrieving the status of an individual JSON object
   * - ``error``
     - If an error, a description of why the status was not retrieved (for example, the specified program was not found)

The ``status`` and ``error`` fields are mutually exclusive meaning if there is an error,
then there will never be a status and vice versa.

For example::

  POST /v3/namespaces/default/status -d '
    [
      { "appId": "MyApp", "programType": "flow", "programId": "MyFlow" },
      { "appId": "MyApp2", "programType": "service", "programId": "MyService" }
    ]

.. highlight:: json-ellipsis

will retrieve the status of two programs. It will return a response such as::

  [
    { "appId":"MyApp", "programType":"flow", "programId":"MyFlow", "status":"RUNNING", "statusCode":200 },
    { "appId":"MyApp2", "programType":"service", "programId":"MyService", "error":"Program not found", "statusCode":404 }
  ]

.. highlight:: console

.. _http-restful-api-lifecycle-schedule:

Schedule Lifecycle
==================
Currently (as of CDAP |release|), schedules can only be created for :ref:`workflows
<workflows>`. Future releases of CDAP will expand this to encompass other program types.

.. _http-restful-api-lifecycle-schedule-add:

Add a Schedule
--------------
To add a schedule for a program to an application, submit an HTTP PUT request::

  PUT /v3/namespaces/<namespace-id>/apps/<app-id>/schedules/<schedule-id>

To add the schedule to an application with a non-default version, submit an HTTP PUT
request with the version specified::

  PUT /v3/namespaces/<namespace-id>/apps/<app-id>/versions/<version-id>/schedules/<schedule-id>

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description
   * - ``namespace-id``
     - Namespace ID
   * - ``app-id``
     - Name of the application
   * - ``schedule-id``
     - Name of the schedule; it is unique to the application and, if specified, the
       application version
   * - ``version-id``
     - Version of the application, typically following `semantic versioning
       <http://semver.org>`__

The request body is a JSON object specifying the details of the schedule to be created::

    {
      "name": "<name of the schedule>",
      "description": "<schedule description>",
      "program": {
        "programName": "<name of the program>",
        "programType": "WORKFLOW"
      },
      "properties": {
        "<key>": "<value>",
        ...
      },
      "constraints": [
        {
          "type": "<constraint type>",
          "waitUntilMet": <boolean>,
          ...
        },
        ...
      ],
      "trigger": {
        "type": "<trigger type>",
        ...
      },
      "timeoutMillis": <timeout in milliseconds>
    }

where a trigger is either a time trigger::

    {
      "type": "TIME",
      "cronExpression": "<cron expression>"
    }

or a partition trigger::

    {
      "type": "PARTITION",
      "dataset": {
        "namespace": "<namespace of the dataset>",
        "dataset": "<name of the dataset>"
      },
      "numPartitions": <required number of partitions>
    }

and a constraint can be one of::

    {
      "type": "CONCURRENCY",
      "maxConcurrency": <max number of runs>,
      "waitUntilMet": <boolean>
    }

    {
      "type": "DELAY",
      "millisAfterTrigger": <milliseconds to delay>,
      "waitUntilMet": <boolean>
    }

    {
      "type": "TIME_RANGE",
      "startTime": "<time in form HH:mm>",
      "endTime": "<time in form HH:mm>",
      "timeZone": "<name of the time zone, e.g., PST>",
      "waitUntilMet": <boolean>
    }

    {
      "type": "LAST_RUN",
      "millisSinceLastRun": <milliseconds since last run>,
      "waitUntilMet": <boolean>
    }


.. highlight:: console

*Note:* For any schedule, the program must be for a workflow and the ``programType`` must be set to ``WORKLFLOW``.

.. rubric:: HTTP Responses

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Status Codes
     - Description
   * - ``409 Conflict``
     - Schedule with the same name already exists

.. _http-restful-api-lifecycle-schedule-update:

Update a Schedule
-----------------
To update a schedule, submit an HTTP POST request::

  POST /v3/namespaces/<namespace-id>/apps/<app-id>/schedules/<schedule-id>/update

To update a schedule of an application with a non-default version, submit an HTTP POST
request with the version specified::

  POST /v3/namespaces/<namespace-id>/apps/<app-id>/versions/<version-id>/schedules/<schedule-id>/update

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description
   * - ``namespace-id``
     - Namespace ID
   * - ``app-id``
     - Name of the application
   * - ``schedule-id``
     - Name of the schedule; it is unique to the application and, if specified, the
       application version
   * - ``version-id``
     - Version of the application, typically following `semantic versioning
       <http://semver.org>`__

The request body is a JSON object specifying the details of the schedule to be updated, and follows
the same form as documented in :ref:`http-restful-api-lifecycle-schedule-add`.

.. highlight:: console

Only changes to the schedule configurations are supported; changes to the schedule name, or to the
program associated with it are not allowed. If *any* properties are provided, they will
overwrite all existing properties with what is provided. You must include all
properties, even ones you are are not altering.

.. rubric:: HTTP Responses

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Status Codes
     - Description
   * - ``400 Bad Request``
     - If the new schedule type does not match the existing schedule type or there are
       other client errors


.. _http-restful-api-lifecycle-schedule-list:

Retrieving a Schedule
---------------------
To retrieve a schedule in an application, submit an HTTP GET request::

  GET /v3/namespaces/<namespace-id>/apps/<app-id>/schedules/<schedule-name>

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description
   * - ``namespace-id``
     - Namespace ID
   * - ``app-id``
     - Name of the application
   * - ``schedule-name``
     - Name of the schedule

The response will contain the schedule in the same form described in :ref:`http-restful-api-lifecycle-schedule-add`.

Note: The response format has changed in CDAP 4.2.0. To retrieve the schedule in the ``ScheduleSpecification``
format that was used in previous versions of CDAP, add a URL parameter ``?format=spec`` to the request URL.

List Schedules
--------------
To list all of the schedules for an application, use an HTTP GET request::

  GET /v3/namespaces/<namespace-id>/apps/<app-id>/schedules

As schedules are created for a workflow, you can also list schedules for a workflow of an application.
You can use the :ref:`http-restful-api-lifecycle-app-deployed-details` to obtain the workflows of an
application.

To list all of the schedules of a workflow of an application, use an HTTP GET request::

  GET /v3/namespaces/<namespace-id>/apps/<app-id>/workflows/<workflow-id>/schedules

The response will contain a list of schedules in the same form as described
in :ref:`http-restful-api-lifecycle-schedule-add`.

Note: The response format has changed in CDAP 4.2.0. To retrieve the schedules in the ``ScheduleSpecification``
format that was used in previous versions of CDAP, add a URL parameter ``?format=spec`` to the request URL.

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description
   * - ``namespace-id``
     - Namespace ID
   * - ``app-id``
     - Name of the application
   * - ``workflow-id``
     - Name of the workflow

Next Scheduled Run Time
-----------------------

To list the next time that the workflow will be be scheduled by a time trigger,
use the parameter ``nextruntime``::

  GET /v3/namespaces/<namespace-id>/apps/<app-id>/workflows/<workflow-id>/nextruntime


.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description
   * - ``namespace-id``
     - Namespace ID
   * - ``app-id``
     - Name of the application
   * - ``workflow-id``
     - Name of the workflow

.. container:: table-block-example

  .. list-table::
     :widths: 99 1
     :stub-columns: 1

     * - Example: Retrieving The Next Runtime
       -

  .. list-table::
     :widths: 15 85
     :class: triple-table

     * - Description
       - Retrieves the next runtime of the workflow *PurchaseHistoryWorkflow* of the
         application *PurchaseHistory*

     * - HTTP Method
       - ``GET /v3/namespaces/default/apps/PurchaseHistory/workflows/PurchaseHistoryWorkflow/nextruntime``

     * - Returns
       - | ``[{"id":"DEFAULT.WORKFLOW:developer:PurchaseHistory:PurchaseHistoryWorkflow:0:DailySchedule","time":1415102400000}]``


.. _http-restful-api-lifecycle-schedule-delete:

Delete a Schedule
-----------------
To delete a schedule, submit an HTTP DELETE::

  DELETE /v3/namespaces/<namespace-id>/apps/<app-id>/schedules/<schedule-id>

To delete a schedule of an application with a non-default version, submit an HTTP DELETE
request with the version specified::

  DELETE /v3/namespaces/<namespace-id>/apps/<app-id>/versions/<version-id>/schedules/<schedule-id>

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description
   * - ``namespace-id``
     - Namespace ID
   * - ``app-id``
     - Name of the application to be deleted
   * - ``schedule-id``
     - Name of the schedule; it is unique to the application and, if specified, the
       application version
   * - ``version-id``
     - Version of the application to be deleted

.. rubric:: HTTP Responses

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Status Codes
     - Description
   * - ``404 Bad Request``
     - If the schedule given by ``schedule-id`` was not found


.. _http-restful-api-lifecycle-schedule-disable-enable:

Schedule: Disable and and Enable
--------------------------------
For a schedule, you can disable and enable it using the RESTful API.

**Disable:** To *disable* a schedule means that the program associated with that schedule will not
trigger again until the schedule is enabled.

**Enable:** To *enable* a schedule means that the trigger is reset, and the program associated will
run again at the next scheduled time.

As a schedule is initially deployed in a *disabled* state, a call to this API is needed to *enable* it.

To disable or enable a schedule use::

  POST /v3/namespaces/<namespace-id>/apps/<app-id>/schedules/<schedule-id>/disable
  POST /v3/namespaces/<namespace-id>/apps/<app-id>/schedules/<schedule-id>/enable

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description
   * - ``namespace-id``
     - Namespace ID
   * - ``app-id``
     - Name of the application
   * - ``schedule-id``
     - Name of the schedule

.. container:: table-block-example

  .. list-table::
     :widths: 99 1
     :stub-columns: 1

     * - Example: Disabling a Schedule
       -

  .. list-table::
     :widths: 15 85
     :class: triple-table

     * - Description
       - Disables the schedule *DailySchedule* of the application *PurchaseHistory*

     * - HTTP Method
       - ``POST /v3/namespaces/default/apps/PurchaseHistory/schedules/DailySchedule/disable``

     * - Returns
       - | ``OK`` if successfully set as disabled


.. _http-restful-api-lifecycle-container-information:

Container Information
=====================
To find out the address of an program's container host and the container’s debug port, you can query
CDAP for a flow or service’s live info via an HTTP GET method::

  GET /v3/namespaces/<namespace-id>/apps/<app-id>/<program-type>/<program-id>/live-info

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description
   * - ``namespace-id``
     - Namespace ID
   * - ``app-id``
     - Name of the application being called
   * - ``program-type``
     - One of ``flows``, ``services``, or  ``workers``
   * - ``program-id``
     - Name of the program (*flow*, *service*, or *worker*)

Example::

  GET /v3/namespaces/default/apps/WordCount/flows/WordCounter/live-info

The response is formatted in JSON; an example of this is shown in
:ref:`CDAP Testing and Debugging <developer:debugging-distributed>`.


.. _http-restful-api-lifecycle-scale:

Scaling
=======

You can retrieve the instance count executing different components from various applications and
different program types using an HTTP POST method::

  POST /v3/namespaces/<namespace-id>/instances

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description
   * - ``namespace-id``
     - Namespace ID

with a JSON array in the request body consisting of multiple JSON objects with these parameters:

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description
   * - ``appId``
     - Name of the application being called
   * - ``programType``
     - One of ``flow``, ``service``, or ``worker``
   * - ``programId``
     - Name of the program (*flow*, *service*, or *worker*) being called
   * - ``runnableId``
     - Name of the *flowlet*, only required if the program type is ``flow``

The response will be the same JSON array as submitted with additional parameters for each
of the underlying JSON objects:

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description
   * - ``requested``
     - Number of instances the user requested for the program defined by the individual JSON object's parameters
   * - ``provisioned``
     - Number of instances that are actually running for the program defined by the individual JSON object's parameters.
   * - ``statusCode``
     - The status code from retrieving the instance count of an individual JSON object
   * - ``error``
     - If an error, a description of why the status was not retrieved (for example, the
       specified program was not found, or the requested JSON object was missing a parameter)

**Note:** The ``requested`` and ``provisioned`` fields are mutually exclusive of the ``error`` field.

.. rubric:: Example

.. list-table::
   :widths: 20 80
   :stub-columns: 1

   * - HTTP Method
     - ``POST /v3/namespaces/default/instances``
   * - HTTP Body
     - ``[{"appId":"MyApp1","programType":"Flow","programId":"MyFlow1","runnableId":"MyFlowlet5"},``
       ``{"appId":"MyApp3","programType":"Service","programId":"MySvc1,"runnableId":"MyHandler1"}]``
   * - HTTP Response
     - ``[{"appId":"MyApp1","programType":"Flow","programId":"MyFlow1",``
       ``"runnableId":"MyFlowlet5","provisioned":2,"requested":2,"statusCode":200},``
       ``{"appId":"MyApp3","programType":"Service","programId":"MySvc1,``
       ``"runnableId":"MyHandler1","statusCode":404,"error":"Runnable: MyHandler1 not found"}]``
   * - Description
     - Attempt to retrieve the instances of the flowlet *MyFlowlet5* in the flow *MyFlow1* in the
       application *MyApp1*, and the service handler *MyHandler1* in the user service
       *MySvc1* in the application *MyApp3*, all in the namespace *default*

.. _rest-scaling-flowlets:

Scaling Flowlets
----------------
You can query and set the number of instances executing a given flowlet
by using the ``instances`` parameter with HTTP GET and PUT methods::

  GET /v3/namespaces/<namespace-id>/apps/<app-id>/flows/<flow-id>/flowlets/<flowlet-id>/instances
  PUT /v3/namespaces/<namespace-id>/apps/<app-id>/flows/<flow-id>/flowlets/<flowlet-id>/instances

with the arguments as a JSON string in the body::

  { "instances" : <quantity> }

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description
   * - ``namespace-id``
     - Namespace ID
   * - ``app-id``
     - Name of the application being called
   * - ``flow-id``
     - Name of the flow
   * - ``flowlet-id``
     - Name of the flowlet
   * - ``quantity``
     - Number of instances to be used

.. rubric:: Examples

- Retrieve the number of instances of the flowlet *saver* in the flow *WhoFlow* of the
  application *HelloWorld* in the namespace *default*::

    GET /v3/namespaces/default/apps/HelloWorld/flows/WhoFlow/flowlets/saver/instances

- Set the number of instances of the flowlet *saver* in the flow *WhoFlow* of the
  application *HelloWorld* in the namespace *default*::

    PUT /v3/namespaces/default/apps/HelloWorld/flows/WhoFlow/flowlets/saver/instances

  .. highlight:: json-ellipsis

  with the arguments as a JSON string in the body::

    { "instances" : 2 }

  .. highlight:: console

Scaling Services
----------------
You can query or change the number of instances of a service
by using the ``instances`` parameter with HTTP GET or PUT methods::

  GET /v3/namespaces/<namespace-id>/apps/<app-id>/services/<service-id>/instances
  PUT /v3/namespaces/<namespace-id>/apps/<app-id>/services/<service-id>/instances

with the arguments as a JSON string in the body::

  { "instances" : <quantity> }

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description
   * - ``namespace-id``
     - Namespace ID
   * - ``app-id``
     - Name of the application
   * - ``service-id``
     - Name of the service
   * - ``quantity``
     - Number of instances to be used

**Note:** You can scale system services using the Monitor HTTP RESTful API
:ref:`Scaling System Services <http-restful-api-monitor-scaling-system-services>`.

.. rubric:: Examples

- Retrieve the number of instances of the service *CatalogLookup* in the application
  *PurchaseHistory* in the namespace *default*::

    GET /v3/namespaces/default/apps/PurchaseHistory/services/CatalogLookup/instances

- Set the number of handler instances of the service *RetrieveCounts*
  of the application *WordCount*::

    PUT /v3/namespaces/default/apps/WordCount/services/RetrieveCounts/instances

  .. highlight:: json-ellipsis

  with the arguments as a JSON string in the body::

    { "instances" : 2 }

  .. highlight:: console

- Using ``curl`` and the :ref:`CDAP Sandbox <sandbox-index>`:

  .. tabbed-parsed-literal::

    $ curl -w"\n" -X PUT "http://localhost:11015/v3/namespaces/default/apps/WordCount/services/RetrieveCounts/instances" \
      -d '{ "instances" : 2 }'

Scaling Workers
---------------
You can query or change the number of instances of a worker by using the ``instances``
parameter with HTTP GET or PUT methods::

  GET /v3/namespaces/<namespace-id>/apps/<app-id>/workers/<worker-id>/instances
  PUT /v3/namespaces/<namespace-id>/apps/<app-id>/workers/<worker-id>/instances

with the arguments as a JSON string in the body::

  { "instances" : <quantity> }

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description
   * - ``namespace-id``
     - Namespace ID
   * - ``app-id``
     - Name of the application
   * - ``worker-id``
     - Name of the worker
   * - ``quantity``
     - Number of instances to be used

.. rubric:: Example

Retrieve the number of instances of the worker *DataWorker* in the application
*HelloWorld* in the namespace *default*::

  GET /v3/namespaces/default/apps/HelloWorld/workers/DataWorker/instances

.. _rest-program-runs:

Run Records
===========

To see all the runs of a selected program (flows, MapReduce programs, Spark programs,
services, or workflows), issue an HTTP GET to the program’s URL with the ``runs``
parameter. This will return a JSON list of all runs for the program, each with a start
time, end time, and program status::

  GET /v3/namespaces/<namespace-id>/apps/<app-id>/<program-type>/<program-id>/runs

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description
   * - ``namespace-id``
     - Namespace ID
   * - ``app-id``
     - Name of the application
   * - ``program-type``
     - One of ``flows``, ``mapreduce``, ``services``, ``spark``, or ``workflows``
   * - ``program-id``
     - Name of the *flow*, *MapReduce*, *custom service*, *Spark*, or *workflow* being called

You can filter the runs by the status of a program, the start and end times,
and can limit the number of returned records:

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Query Parameter
     - Description
   * - ``status``
     - running/completed/failed
   * - ``start``
     - start timestamp
   * - ``end``
     - end timestamp
   * - ``limit``
     - maximum number of returned records

The result returned will include the *runid* field, a UUID that uniquely identifies a run within CDAP,
with the start and end times in seconds since the start of the Epoch (midnight 1/1/1970).
Use that runid in subsequent calls to obtain additional information.

.. container:: table-block-example

  .. list-table::
     :widths: 99 1
     :stub-columns: 1

     * - Example: Retrieving Run Records
       -

  .. list-table::
     :widths: 15 85
     :class: triple-table

     * - Description
       - Retrieve the run records of the flow *WhoFlow* of the application *HelloWorld*

     * - HTTP Method
       - ``GET /v3/namespaces/default/apps/HelloWorld/flows/WhoFlow/runs``

     * - Returns
       - | ``{"runid":"...","start":1382567598,"status":"RUNNING"},``
         | ``{"runid":"...","start":1382567447,"end":1382567492,"status":"STOPPED"},``
         | ``{"runid":"...","start":1382567383,"end":1382567397,"status":"STOPPED"}``


Retrieving Specific Run Information
-----------------------------------

To fetch the run record for a particular run of a program, use::

  GET /v3/namespaces/<namespace-id>/apps/<app-id>/<program-type>/<program-id>/runs/<run-id>


.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description
   * - ``namespace-id``
     - Namespace ID
   * - ``app-id``
     - Name of the application
   * - ``program-type``
     - One of ``flows``, ``mapreduce``, ``services``, ``spark``, or ``workflows``
   * - ``program-id``
     - Name of the *flow*, *MapReduce*, *custom service*, *Spark*, or *workflow* being called
   * - ``run-id``
     - Run id of the run


.. container:: table-block-example

  .. list-table::
     :widths: 99 1
     :stub-columns: 1

     * - Example: Retrieving A Particular Run Record
       -

  .. list-table::
     :widths: 15 85
     :class: triple-table

     * - Description
       - Retrieve the run record of the flow *WhoFlow* of the application *HelloWorld* for
         run *b78d0091-da42-11e4-878c-2217c18f435d*

     * - HTTP Method
       - ``GET /v3/namespaces/default/apps/HelloWorld/flows/WhoFlow/runs/b78d0091-da42-11e4-878c-2217c18f435d``

     * - Returns
       - | ``{"runid":"...","start":1382567598,"status":"RUNNING"}``


For services, you can retrieve:

- the history of successfully completed Apache Twill service runs using::

    GET /v3/namespaces/<namespace-id>/apps/<app-id>/services/<service-id>/runs?status=completed

For workflows, you can retrieve:

- the information about the currently running node(s) in the workflow::

    GET /v3/namespaces/<namespace-id>/apps/<app-id>/workflows/<workflow-id>/runs/<run-id>/current

- the schedules defined for a workflow (using the parameter ``schedules``)::

    GET /v3/namespaces/<namespace-id>/apps/<app-id>/workflows/<workflow-id>/schedules

- the next time that the workflow is scheduled to run (using the parameter ``nextruntime``)::

    GET /v3/namespaces/<namespace-id>/apps/<app-id>/workflows/<workflow-id>/nextruntime


.. rubric:: Examples

.. container:: table-block-example

  .. list-table::
     :widths: 99 1
     :stub-columns: 1

     * - Example: Retrieving The Most Recent Run
       -

  .. list-table::
     :widths: 15 85
     :class: triple-table

     * - Description
       - Retrieve the most recent successful completed run of the service *CatalogLookup* of the application *PurchaseHistory*

     * - HTTP Method
       - ``GET /v3/namespaces/default/apps/PurchaseHistory/services/CatalogLookup/runs?status=completed&limit=1``

     * - Returns
       - | ``[{"runid":"cad83d45-ecfb-4bf8-8cdb-4928a5601b0e","start":1415051892,"end":1415057103,"status":"STOPPED"}]``


.. _http-restful-api-lifecycle-workflow-runs-suspend-resume:

Workflow Runs: Suspend and Resume
---------------------------------

For workflows, in addition to :ref:`starting <http-restful-api-lifecycle-start>` and
:ref:`stopping <http-restful-api-lifecycle-stop>`, you can suspend and resume individual
runs of a workflow using the RESTful API.

**Suspend:** To *suspend* means that the current activity will be taken to completion, but no further
programs will be initiated. Programs will not be left partially uncompleted, barring any errors.

In the case of a workflow with multiple MapReduce programs, if one of them is running (first of
three perhaps) and you suspend the workflow, that first MapReduce will be completed but the
following two will not be started.

**Resume:** To *resume* means that activity will start up where it was left off, beginning with the start
of the next program in the sequence.

In the case of the workflow mentioned above, resuming it after suspension would start up with the
second of the three MapReduce programs, which is where it would have left off when it was suspended.

With workflows, *suspend* and *resume* require a *run-id* as the action takes place on
either a currently running or suspended workflow.

To suspend or resume a workflow, use::

  POST /v3/namespaces/<namespace-id>/apps/<app-id>/workflows/<workflow-id>/runs/<run-id>/suspend
  POST /v3/namespaces/<namespace-id>/apps/<app-id>/workflows/<workflow-id>/runs/<run-id>/resume

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description
   * - ``namespace-id``
     - Namespace ID
   * - ``app-id``
     - Name of the application
   * - ``workflow-id``
     - Name of the workflow
   * - ``run-id``
     - UUID of the workflow run

.. container:: table-block-example

  .. list-table::
     :widths: 99 1
     :stub-columns: 1

     * - Example: Suspending a Workflow
       -

  .. list-table::
     :widths: 15 85
     :class: triple-table

     * - Description
       - Suspends the run ``0ce13912-e980-11e4-a7d7-8cae4cfd0e64`` of the workflow
         *PurchaseHistoryWorkflow* of the application *PurchaseHistory*

     * - HTTP Method
       - ``POST /v3/namespaces/default/apps/PurchaseHistory/workflows/PurchaseHistoryWorkflow/runs/0ce13912-e980-11e4-a7d7-8cae4cfd0e64/suspend``

     * - Returns
       - | ``Program run suspended.`` if successfully set as suspended
