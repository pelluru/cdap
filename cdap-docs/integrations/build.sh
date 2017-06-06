#!/usr/bin/env bash

# Copyright © 2014-2017 Cask Data, Inc.
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

# Build script for docs

source ../vars
source ../_common/common-build.sh

CHECK_INCLUDES=${TRUE}

function download_includes() {
  local target_includes_dir=${1}
  local branch
  if [ "x${LOCAL_INCLUDES}" == "x${TRUE}" ]; then
    echo_red_bold "Copying local copy of Apache Sentry File..."
    local base_source="file://${PROJECT_PATH}/../cdap-security-extn/"
  else
    echo_red_bold "Downloading Apache Sentry File from GitHub repo caskdata/cdap-security-extn..."
    local base_source="https://raw.githubusercontent.com/caskdata/cdap-security-extn/"
    if [ "x${GIT_BRANCH_TYPE:0:7}" == "xdevelop" ]; then
      local branch="develop/"
    else
      local branch="${GIT_BRANCH_CDAP_SECURITY_EXTN}/"
    fi
  fi
  local file_source="${base_source}${branch}cdap-sentry/cdap-sentry-extension/"
  # Download Apache Sentry File
  download_file ${target_includes_dir} ${file_source} README.rst bcf5148f45a778a7bc9f842ba84cbc10 cdap-sentry-extension-readme.txt
}

run_command ${1}
