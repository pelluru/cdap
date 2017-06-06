===============================
CDAP Integration Test Framework
===============================

Users can use ``IntegrationTestBase`` to write tests that run against a framework-provided
CDAP Sandbox or a remote CDAP instance.


Running tests using the framework-provided CDAP Sandbox instance
======================================================================

::

  cd <your-test-module>
  mvn test


Running tests against a remote CDAP instance
============================================

::

  cd <your-test-module>
  mvn test -DargLine="-DinstanceUri=<instance URI> -Dcdap.username=<username> -Dcdap.password=<password> -Dtest.namespace=<test namespace> -DverifySSL=<verify ssl>"

- ``<instance URI>`` is the URI used to connect to your CDAP router
  (for example, ``http://example.com:11015``)
- ``<username>`` and ``<password>`` are the credentials for your CDAP authentication server by
  **Note:** These are unnecessary in a non-secure CDAP instance.
- ``<verify ssl>`` is whether to verify the certificate in SSL connections.
  **Note:** This is unnecessary in a non-SSL CDAP instance.
- ``<test namespace>`` is the namespace in which applications will be deployed in and tests executed against.
  Defaults to the ``default`` namespace. If this namespace already exists, it will be cleared, but not deleted
  upon test teardown. Otherwise, it will be deleted completely upon test teardown.

For example, to run tests against a CDAP instance at ``http://example.com:11015`` with
user ``abc123`` and password ``123456``::

  mvn test -DargLine="-DinstanceUri=http://example.com:11015 -Dcdap.username=abc123 -Dcdap.password=123456"
