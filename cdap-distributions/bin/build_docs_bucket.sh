#!/usr/bin/env bash
#
# Copyright © 2016-2017 Cask Data, Inc.
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

# Logic:
# get local version
# get s3 version file
# check if version matches
# - no, sync version to target dir
# - yes, done

# Find our location and base repo directory
# Resolve links: $0 may be a link
PRG=${0}
# Need this for relative symlinks.
while [ -h ${PRG} ]; do
    ls=`ls -ld ${PRG}`
    link=`expr ${ls} : '.*-> \(.*\)$'`
    if expr ${link} : '/.*' > /dev/null; then
        PRG=${link}
    else
        PRG=`dirname ${PRG}`/${link}
    fi
done
cd `dirname ${PRG}`/.. >&-
DISTRIBUTIONS_HOME=`pwd -P`
cd `dirname ${DISTRIBUTIONS_HOME}` >&-
REPO_HOME=`pwd -P`
DOCS_HOME=${REPO_HOME}/cdap-docs

TARGET_DIR=${DOCS_HOME}/target

S3_BUCKET=${S3_BUCKET:-docs.cask.co}
S3_REPO_PATH=${S3_REPO_PATH:-cdap} # No leading or trailing slashes
VERSION=${VERSION:-4.2.0-SNAPSHOT}

function die() { __code=${2:-1}; echo "[ERROR] ${1}" >&2; exit ${__code}; };

function compare_versions() {
  [[ ${1} == ${2} ]] && return 0
  local OLDIFS=${IFS}
  local IFS=.
  local i ver1=(${1}) ver2=(${2})
  local IFS=${OLDIFS}
  # fill empty fields in ver1 with zeros
  for ((i=${#ver1[@]}; i<${#ver2[@]}; i++)); do
    ver1[i]=0
  done
  for ((i=0; i<${#ver1[@]}; i++)); do
    if [[ -z ${ver2[i]} ]]; then
      # fill empty fields in ver2 with zeros
      ver2[i]=0
    fi
    if ((10#${ver1[i]} > 10#${ver2[i]})); then
      return 1
    fi
    if ((10#${ver1[i]} < 10#${ver2[i]})); then
      return 2
    fi
  done
  return 0
}

function get_repo_version() {
  local __opts="--quiet --force"
  s3cmd get ${__opts} s3://${S3_BUCKET}/${S3_REPO_PATH}/version repo_version
  __ret=$?
  if [[ ${__ret} -ne 0 ]]; then
    echo "ERROR: Failed to fetch version file from S3"
    # exit rather than return since we're assigning to variable
    exit ${__ret}
  fi
  echo $(<repo_version)
}

function sync_from_s3() {
  s3cmd sync --no-preserve s3://${S3_BUCKET}/${S3_REPO_PATH}/${__repo_version}/ ${TARGET_DIR}/${__repo_version}/
}

function robots_tags() {
  local __dir=${1}
  python ${DOCS_HOME}/tools/docs-change.py --robots ${__dir}
}

if [[ ${VERSION} =~ -SNAPSHOT ]]; then
  # SNAPSHOT should be tagged by build
  echo "SNAPSHOT detected: exiting"
  exit 0
else
  __repo_version=$(get_repo_version)
  compare_versions ${VERSION} ${__repo_version}
  __ret=$?

  case ${__ret} in
    1) # Local version is greater
      echo "Local version is greater: adding robots tags to ${__repo_version}"
      sync_from_s3 || die "Failed to sync ${__repo_version} from S3"
      robots_tags ${TARGET_DIR}/${__repo_version} || die "Failed to add robots tags to ${__repo_version}"
      ;;
    2) # Remote version is greater
      echo "Remote version is greater: adding robots tags to ${VERSION}"
      robots_tags ${TARGET_DIR}/${VERSION} || die "Failed to add robots tags to ${VERSION}"
      ;;
    0) # Same version
      echo "Local version is same as remote version: skipping tagging"
      ;;
    *) die "Something went terribly wrong in comparing versions"
  esac
fi

echo "Completed processing docs for upload"
exit 0 # We made it!
