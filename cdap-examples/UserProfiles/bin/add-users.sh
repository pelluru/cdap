#!/usr/bin/env bash

#
# Copyright © 2015 Cask Data, Inc.
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

bin=`dirname "${BASH_SOURCE-$0}"`
bin=`cd "$bin"; pwd`
script=`basename $0`

auth_token=
auth_file="$HOME/.cdap.accesstoken"
verbose=false
delete=false

function get_auth_token() {
  if [ -f $auth_file ]; then
    auth_token=`cat $auth_file`
  fi
}

function usage() {
  echo "Tool for adding users to the profiles table."
  echo "Usage: $script [--host <hostname>]"
  echo ""
  echo "  Options"
  echo "    --host      Specifies the host that CDAP is running on. (Default: localhost)"
  echo "    --delete    Delete the users instead of adding them"
  echo "    --verbose   Print some information"
  echo "    --help      This help message"
  echo ""
}

gateway="localhost"
stream="events"
while [ $# -gt 0 ]
do
  case "$1" in
    --host) shift; gateway="$1"; shift;;
    --verbose) shift; verbose=true;;
    --delete) shift; delete=true;;
    *)  usage; exit 1
   esac
done

#  get the access token
get_auth_token

OLD_IFS=IFS
IFS=$'\n'
lines=`cat "$bin"/../resources/users.txt`
for line in $lines
do
  userid=`echo $line | awk -F\" '{ print $4 }'`
  if [ $delete == "true" ]; then
    if [ $verbose == "true" ]; then
      echo Deleting user id: $userid
    fi
    status=`curl -qSfsw "%{http_code}\\n" -H "Authorization: Bearer $auth_token" -X DELETE \
      http://$gateway:11015/v3/namespaces/default/apps/UserProfiles/services/UserProfileService/methods/profiles/$userid`
    expected=200;
  else
    if [ $verbose == "true" ]; then
      echo Creating user id: $userid
    fi
    status=`curl -qSfsw "%{http_code}\\n" -H "Authorization: Bearer $auth_token" -X PUT -d "$line" \
      http://$gateway:11015/v3/namespaces/default/apps/UserProfiles/services/UserProfileService/methods/profiles/$userid`
    expected=201;
  fi
  if [ $status -ne $expected ]; then
    echo "Failed to send data."
    if [ $status == 401 ]; then
      if [ "x$auth_token" == "x" ]; then
        echo "No access token provided"
      else
        echo "Invalid access token"
      fi
    fi
    echo "Exiting program..."
    exit 1;
  fi
done
IFS=$OLD_IFS
