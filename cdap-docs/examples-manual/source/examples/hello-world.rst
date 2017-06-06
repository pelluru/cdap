.. meta::
    :author: Cask Data, Inc.
    :description: Cask Data Application Platform Hello World Example
    :copyright: Copyright © 2014 Cask Data, Inc.

.. _examples-hello-world:

===========
Hello World
===========

The simplest Cask Data Application Platform (CDAP) example.


Overview
========
This application uses one stream, one dataset, one flow, and one service to implement the classic "Hello World":

- A stream to send names to;
- A flow with a single flowlet that reads the stream and stores in a dataset each name in a ``KeyValueTable``; and
- A service that reads the name from the ``KeyValueTable`` and responds with ``"Hello [Name]!"``

The *HelloWorld* Application
----------------------------
.. literalinclude:: /../../../cdap-examples/HelloWorld/src/main/java/co/cask/cdap/examples/helloworld/HelloWorld.java
   :language: java
   :lines: 48-58
   :append: ...

The application uses a stream called *who* to ingest data through a flow *WhoFlow* to a dataset *whom*.

The *WhoFlow*
-------------
This is a trivial flow with a single flowlet named *saver* of type ``NameSaver``:

.. literalinclude:: /../../../cdap-examples/HelloWorld/src/main/java/co/cask/cdap/examples/helloworld/HelloWorld.java
   :language: java
   :lines: 63-72
   :dedent: 2

The flowlet uses a dataset of type ``KeyValueTable`` to store the names it reads from the stream. Every time a new
name is received, it is stored in the table under the key ``name``, and it overwrites any name that was previously
stored:

.. literalinclude:: /../../../cdap-examples/HelloWorld/src/main/java/co/cask/cdap/examples/helloworld/HelloWorld.java
   :language: java
   :lines: 77-98
   :dedent: 2

Note that the flowlet also emits metrics: every time a name longer than 10 characters is received,
the counter ``names.longnames`` is incremented by one, and the metric ``names.bytes`` is incremented
by the length of the name. We will see below how to retrieve these metrics using the
:ref:`http-restful-api-metrics`.

The *Greeting* Service
----------------------
This is a simple service. It has only one handler, the class ``GreetingHandler``.

.. literalinclude:: /../../../cdap-examples/HelloWorld/src/main/java/co/cask/cdap/examples/helloworld/HelloWorld.java
   :language: java
   :lines: 103-113
   :dedent: 2

The *GreetingHandler* Handler
-----------------------------
This has a single endpoint called ``greet`` that does not accept arguments. When invoked, it
reads the name stored by the ``NameSaver`` from the key-value table. It returns a simple
greeting containing that name:

.. literalinclude:: /../../../cdap-examples/HelloWorld/src/main/java/co/cask/cdap/examples/helloworld/HelloWorld.java
   :language: java
   :lines: 118-135
   :dedent: 2

Note that the service, like the flowlet, also emits metrics: every time the name *Jane Doe* is received,
the counter ``greetings.count.jane_doe`` is incremented by one.
We will see below how to retrieve this metric using the
:ref:`http-restful-api-metrics`.


.. Building and Starting
.. =====================
.. |example| replace:: HelloWorld
.. |example-italic| replace:: *HelloWorld*
.. |application-overview-page| replace:: :cdap-ui-apps-programs:`application overview page, programs tab <HelloWorld>`

.. include:: _includes/_building-starting-running.txt


Running the Example
===================

.. Starting the Flow
.. -----------------
.. |example-flow| replace:: WhoFlow
.. |example-flow-italic| replace:: *WhoFlow*

.. include:: _includes/_starting-flow.txt

.. Starting the Service
.. --------------------
.. |example-service| replace:: Greeting
.. |example-service-italic| replace:: *Greeting*

.. include:: _includes/_starting-service.txt

Injecting a Name
----------------
In the |application-overview-page|, click on |example-flow-italic|.
This takes you to the flow details page. (If you haven't already started the flow, click
on the *Start* button in the right-side, below the green arrow.) The flow's *status* will
read *Running* when it is ready to receive events.

Now double-click on the *who* stream on the left side of the flow visualization, which brings up
a pop-up window. Enter a name and click the *Inject* button. After you close the pop-up
window, you will see that the counters for both the stream and the *saver* flowlet
increase to 1. You can repeat this step to enter more names, but remember that only the
last name is stored in the key-value table.

Metrics are collected based on the ``bytes`` metric (the total number of bytes of names),
the ``longnames`` metric (the number of names, each greater than 10 characters), and the
``greetings.count.jane_doe`` metric (the number of times the name *Jane Doe* has been
"greeted").

To try out these metrics, first send a few long names (each greater than 10 characters)
and send *Jane Doe* a number of times.

You can also use the CDAP CLI:

.. tabbed-parsed-literal::

  $ cdap cli send stream who "'Alice Cumberbund'"
  $ cdap cli send stream who "Bob"
  $ cdap cli send stream who "'Jane Doe'"
  $ cdap cli send stream who "Tom"
  ...


Using the Service
-----------------
Go back to the |application-overview-page|, and click on the *Greeting* service. (If you
haven't already started the service, click on the *Start* button on the right-side.) The
service's label will read *Running* when it is ready to receive events.

Now you can make a request to the service using ``curl``:

.. tabbed-parsed-literal::

  $ curl -w"\n" -X GET "http://localhost:11015/v3/namespaces/default/apps/HelloWorld/services/Greeting/methods/greet"

If the last name you entered was *Tom*, the service will respond with ``Hello Tom!``

There is a *Make Request* button in the :cdap-ui-apps:`CDAP UI, Greeting service
<HelloWorld/programs/services/Greeting/runs/>` that will make the same request, with a
similar response.


Retrieving Metrics
------------------
.. highlight:: console

You can now query the metrics that are emitted by the flow and service. The results you
receive will vary depending on the entries you have made to the flow. If a particular
metric has no value, it will return an empty array in the ``"series"`` of the results,
such as::

  {"startTime":0,"endTime":1429475995,"series":[]}

To see the value of the ``names.bytes`` metric, you can make an HTTP request to the
:ref:`http-restful-api-metrics` using curl:

.. tabbed-parsed-literal::

  $ curl -w"\n" -X POST "http://localhost:11015/v3/metrics/query?tag=namespace:default&tag=app:HelloWorld&tag=flow:WhoFlow&tag=flowlet:saver&metric=user.names.bytes&aggregate=true"

  {"startTime":0,"endTime":1458877439,"series":[{"metricName":"user.names.bytes","grouping":{},"data":[{"time":0,"value":79}]}],"resolution":"2147483647s"}

To see the value of the ``names.longnames`` metric (the number of names, each of which is greater than 10 characters in length),
you can use:

.. tabbed-parsed-literal::

  $ curl -w"\n" -X POST "http://localhost:11015/v3/metrics/query?tag=namespace:default&tag=app:HelloWorld&tag=flow:WhoFlow&tag=flowlet:saver&metric=user.names.longnames&aggregate=true"

  {"startTime":0,"endTime":1458877544,"series":[{"metricName":"user.names.longnames","grouping":{},"data":[{"time":0,"value":3}]}],"resolution":"2147483647s"}

To see the value of the ``greetings.count.jane_doe`` metric (the number of times the specific name *Jane Doe* has been "greeted"),
you can use:

.. tabbed-parsed-literal::

  $ curl -w"\n" -X POST "http://localhost:11015/v3/metrics/query?tag=namespace:default&tag=app:HelloWorld&tag=service:Greeting&metric=user.greetings.count.jane_doe&aggregate=true"

  {"startTime":0,"endTime":1458877575,"series":[{"metricName":"user.greetings.count.jane_doe","grouping":{},"data":[{"time":0,"value":2}]}],"resolution":"2147483647s"}


.. Stopping and Removing the Application
.. =====================================
.. include:: _includes/_stopping-flow-service-removing-application.txt
