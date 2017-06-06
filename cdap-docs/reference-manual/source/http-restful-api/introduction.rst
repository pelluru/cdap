.. meta::
    :author: Cask Data, Inc.
    :description: HTTP RESTful Interface to the Cask Data Application Platform
    :copyright: Copyright © 2014-2017 Cask Data, Inc.

.. _http-restful-api-introduction:

============
Introduction
============

.. highlight:: console

.. _http-restful-api-conventions:

Conventions
============

In this API, *client* refers to an external application that is calling CDAP using the HTTP interface.
*application* refers to a user application that has been deployed into CDAP.

.. _http-restful-api-conventions-base-url:

Base URL
--------
All URLs referenced in these APIs have this base URL::

  http://<host>:<port>

where:

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description
   * - ``host``
     - Host name of the CDAP server
   * - ``port``
     - Port set as the ``router.bind.port`` in ``cdap-site.xml`` (default: **11015**)


**Note:** If SSL is enabled for CDAP, then the base URL uses ``https`` instead and ``port`` becomes the port that is set
as the ``router.ssl.server.port`` in ``cdap-site.xml`` (default: 10443).

In this API, each endpoint is documented with the HTTP method for the request and a
resource identifier. The base URL is assumed to precede each API's resource identifier.
For example, the endpoint documentation for creating a stream is::

  PUT /v3/namespaces/<namespace-id>/streams/<new-stream-id>

This means you would use::

  PUT http://<host>:<port>/v3/namespaces/<namespace-id>/streams/<new-stream-id>

If you are using the CDAP Sandbox, running on your local machine, you might make a ``curl`` call such as:

.. tabbed-parsed-literal::

  $ curl -w"\n" -X PUT "http://localhost:11015/v3/namespaces/default/streams/who"

Variable Replacement
--------------------
Text that are variables that you are to replace is indicated by a series of angle brackets (``< >``). For example::

  PUT /v3/namespaces/<namespace-id>/streams/<new-stream-id>

indicates that text such as ``<namespace-id>`` and ``<new-stream-id>`` are variables and that
you are to replace them with your values, perhaps in this case the namespace *default* and
the stream *mystream*::

  PUT /v3/namespaces/default/streams/mystream


.. _http-restful-api-conventions-reserved-unsafe-characters:

Reserved and Unsafe Characters
------------------------------
In path parameters, reserved and unsafe characters must be replaced with their equivalent
percent-encoded format, using the "``%hh``" syntax, as described in
`RFC3986: Uniform Resource Identifier (URI): Generic Syntax <http://tools.ietf.org/html/rfc3986#section-2.1>`__.

In general, any character that is not a letter, a digit, or one of ``$-_.+!*'()`` should be encoded.

See the section on :ref:`Path Parameters<services-path-parameters>` for suggested approaches to
encoding parameters.

Additionally, there are further restrictions on the characters used in certain parameters such as
namespaces.


.. _http-restful-api-namespace-characters:

Names and Characters for Namespace Identifiers
----------------------------------------------
Namespaces have a limited set of characters allowed in their identifier; they are
restricted to letters (a-z, A-Z), digits (0-9), hyphens (-), and underscores (_). There is
no size limit on the length of a namespace identifier nor on the number of namespaces.

The three namespaces ``cdap``, ``default``, and ``system`` are reserved. The ``cdap``
and ``system`` namespaces cannot be used by users directly. The ``default`` namespace,
however,  can be used by anyone.

All reserved namespaces cannot be deleted.


.. _http-restful-api-status-codes:

Status Codes
============
`Common status codes <http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html>`__ returned for all HTTP calls:

.. list-table::
   :widths: 10 30 60
   :header-rows: 1

   * - Code
     - Description
     - Explanation
   * - ``200``
     - ``OK``
     - The request returned successfully
   * - ``400``
     - ``Bad Request``
     - The request had a combination of parameters that is not recognized
   * - ``401``
     - ``Unauthorized``
     - The request did not contain an authentication token; see the section below on
       :ref:`http-restful-api-working-with-cdap-security`
   * - ``403``
     - ``Forbidden``
     - The request was authenticated but the client does not have permission; requests can
       fail due to a lack of privilege, as described in the section below on
       :ref:`http-restful-api-working-with-cdap-security`
   * - ``404``
     - ``Not Found``
     - The request did not address any of the known URIs
   * - ``405``
     - ``Method Not Allowed``
     - A request was received with a method not supported for the URI
   * - ``409``
     - ``Conflict``
     - A request could not be completed due to a conflict with the current resource state
   * - ``500``
     - ``Internal Server Error``
     - An internal error occurred while processing the request
   * - ``501``
     - ``Not Implemented``
     - A request contained a query that is not supported by this API

**Note:** These returned status codes are not necessarily included in the descriptions of
the APIs, but a request may return any of these.

Converting from V2 APIs
=======================
If you are converting code from the earlier HTTP RESTful API v2, the
simplest way to convert your code is to use the ``default`` namespace, which is pre-existing
in CDAP. Example::

  PUT http://<host>:<port>/v2/streams/<new-stream-id>

can be replaced with::

  PUT http://<host>:<port>/v3/namespaces/default/streams/<new-stream-id>

However, you will need to test your code, as many APIs have changed as a result of the
addition of namespaces.

.. _http-restful-api-working-with-cdap-security:

Working with CDAP Security
==========================
- When working with a CDAP cluster with **security enabled** (``security.enabled=true`` in
  ``cdap-site.xml``), all calls to the HTTP RESTful APIs must be authenticated. Clients must
  first obtain an access token from the authentication server (see the :ref:`Client
  Authentication <client-authentication>` section of the :ref:`developer:developer-index`).
  In order to authenticate, all client requests must supply this access token in the
  ``Authorization`` header of the request::

    Authorization: Bearer <token>

  For CDAP-issued access tokens, the authentication scheme must always be ``Bearer``.

- When working with a CDAP cluster with **authorization enabled**
  (``security.authorization.enabled=true`` in ``cdap-site.xml``), all calls to the HTTP
  RESTful APIs must be authorized. Clients must be privileged, following the polices
  described in :ref:`security-authorization-policies`.
