/*
 * Copyright © 2017 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package co.cask.cdap.proto.artifact;

import co.cask.cdap.api.artifact.ArtifactRange;
import co.cask.cdap.api.artifact.ArtifactVersionRange;
import co.cask.cdap.api.artifact.InvalidArtifactRangeException;
import co.cask.cdap.proto.Id;
import co.cask.cdap.proto.id.NamespaceId;

/**
 * Utility class for parsing {@link ArtifactRange}
 */
public final class ArtifactRanges {

  private ArtifactRanges() {

  }

  /**
   * Parses the string representation of an artifact range, which is of the form:
   * {namespace}:{name}[{lower-version},{upper-version}]. This is what is returned by {@link #toString()}.
   * For example, default:my-functions[1.0.0,2.0.0) will correspond to an artifact name of my-functions with a
   * lower version of 1.0.0 and an upper version of 2.0.0 in the default namespace.
   *
   * @param artifactRangeStr the string representation to parse
   * @return the ArtifactRange corresponding to the given string
   */
  public static ArtifactRange parseArtifactRange(String artifactRangeStr) throws InvalidArtifactRangeException {
    // get the namespace
    int nameStartIndex = artifactRangeStr.indexOf(':');
    if (nameStartIndex < 0) {
      throw new InvalidArtifactRangeException(
        String.format("Invalid artifact range %s. Could not find ':' separating namespace from artifact name.",
                      artifactRangeStr));
    }
    String namespaceStr = artifactRangeStr.substring(0, nameStartIndex);

    try {
      NamespaceId namespace = new NamespaceId(namespaceStr);
    } catch (Exception e) {
      throw new InvalidArtifactRangeException(String.format("Invalid namespace %s: %s",
                                                            namespaceStr, e.getMessage()));
    }

    // check not at the end of the string
    if (nameStartIndex == artifactRangeStr.length()) {
      throw new InvalidArtifactRangeException(
        String.format("Invalid artifact range %s. Nothing found after namespace.", artifactRangeStr));
    }

    return parseArtifactRange(namespaceStr, artifactRangeStr.substring(nameStartIndex + 1));
  }

  /**
   * Parses an unnamespaced string representation of an artifact range. It is expected to be of the form:
   * {name}[{lower-version},{upper-version}]. Square brackets are inclusive, and parentheses are exclusive.
   * For example, my-functions[1.0.0,2.0.0) will correspond to an artifact name of my-functions with a
   * lower version of 1.0.0 and an upper version of 2.0.0.
   *
   * @param namespace the namespace of the artifact range
   * @param artifactRangeStr the string representation to parse
   * @return the ArtifactRange corresponding to the given string
   */
  public static ArtifactRange parseArtifactRange(String namespace,
                                                 String artifactRangeStr) throws InvalidArtifactRangeException {
    // search for the '[' or '(' between the artifact name and lower version
    int versionStartIndex = indexOf(artifactRangeStr, '[', '(', 0);
    if (versionStartIndex < 0) {
      throw new InvalidArtifactRangeException(
        String.format("Invalid artifact range %s. " +
                        "Could not find '[' or '(' indicating start of artifact lower version.", artifactRangeStr));
    }
    String name = artifactRangeStr.substring(0, versionStartIndex);

    if (!Id.Artifact.isValidName(name)) {
      throw new InvalidArtifactRangeException(
        String.format("Invalid artifact range %s. Artifact name '%s' is invalid.", artifactRangeStr, name));
    }

    return new ArtifactRange(namespace, name,
                             ArtifactVersionRange.parse(artifactRangeStr.substring(versionStartIndex)));
  }

  // like String's indexOf(char, int), except it looks for either one of 2 characters
  private static int indexOf(String str, char option1, char option2, int startIndex) {
    for (int i = startIndex; i < str.length(); i++) {
      char charAtIndex = str.charAt(i);
      if (charAtIndex == option1 || charAtIndex == option2) {
        return i;
      }
    }
    return -1;
  }
}
