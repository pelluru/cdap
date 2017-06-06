===========================================================
Cask Data Application Platform (CDAP) Application Archetype
===========================================================

This directory contains a CDAP Application Archetype for the Cask Data Application Platform (CDAP).


Installing
==========

To install the archetype, enter in the CDAP root directory::

  $ mvn clean install -f cdap-archetypes/cdap-app-archetype/pom.xml


Creating
========

To create a project from the archetype, use this script as an example
(substituting your version of CDAP for ${cdap.version} as appropriate)::

  mvn archetype:generate
    -DarchetypeGroupId=co.cask.cdap
    -DarchetypeArtifactId=cdap-app-archetype
    -DarchetypeVersion=${cdap.version}
    -DgroupId=com.example
    -DartifactId=MyExample
    -Dversion=1.0-SNAPSHOT


To confirm project creation, type Y and press ENTER.

License and Trademarks
======================

Copyright © 2014 Cask Data, Inc.

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
in compliance with the License. You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the 
License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, 
either express or implied. See the License for the specific language governing permissions 
and limitations under the License.

Cask is a trademark of Cask Data, Inc. All rights reserved.

Apache, Apache HBase, and HBase are trademarks of The Apache Software Foundation. Used with
permission. No endorsement by The Apache Software Foundation is implied by the use of these marks.
