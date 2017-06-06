#!/bin/bash
#
# Copyright © 2015-2016 Cask Data, Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License. You may obtain a copy of
# the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
# License for the specific language governing permissions and limitations under
# the License.

#
# Stop SDK and remove data directory
#

# Stop SDK
/etc/init.d/cdap-sdk stop

# Remove data and logs directories
rm -rf /opt/cdap/sdk/data /opt/cdap/sdk/logs

# Make cdap own /opt/cdap
chown -R cdap:cdap /opt/cdap

exit 0
