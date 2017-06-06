/*
 * Copyright © 2015 Cask Data, Inc.
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

package co.cask.cdap.api.artifact;

import co.cask.cdap.api.annotation.Beta;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * Represents version of an artifact. It will try to parse the version string of format
 *
 * <pre>
 * {@code
 * parse version string in format of [major].[minor].[fix](-|.)[suffix]
 * }
 * </pre>
 */
@Beta
public final class ArtifactVersion implements Comparable<ArtifactVersion> {

  private static final String VERSION_REGEX = "(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?(?:[.\\-](.*))?$";
  private static final Pattern PATTERN = Pattern.compile(VERSION_REGEX);
  private static final Pattern SUFFIX_PATTERN = Pattern.compile("\\-" + VERSION_REGEX);

  private final String version;
  private final Integer major;
  private final Integer minor;
  private final Integer fix;
  private final String suffix;

  /**
   * Constructs an instance by parsing the given string. If the string does not match the version pattern,
   * {@link #getVersion()} will return null.
   *
   * @param str the version string. The whole string needs to match with the version pattern supported by this class.
   */
  public ArtifactVersion(String str) {
    this(str, false);
  }

  /**
   * Constructs an instance by parsing the given string. If the string does not match the version pattern,
   * {@link #getVersion()} will return null.
   *
   * @param str the version string.
   * @param matchSuffix if {@code true}, try to match the version pattern by the suffix of the string. Otherwise match
   *                    the whole string.
   */
  public ArtifactVersion(String str, boolean matchSuffix) {
    String tmpVersion = null;
    Integer major = null;
    Integer minor = null;
    Integer fix = null;
    String suffix = null;
    if (str != null) {
      Matcher matcher = matchSuffix ? SUFFIX_PATTERN.matcher(str) : PATTERN.matcher(str);
      boolean matches = matchSuffix ? (matcher.find()) : matcher.matches();

      if (matches) {
        tmpVersion = matchSuffix ? matcher.group(0).substring(1) : matcher.group(0);
        major = valueOf(matcher.group(1));
        minor = valueOf(matcher.group(2));
        fix = valueOf(matcher.group(3));
        suffix = matcher.group(4);
      }
    }

    this.version = tmpVersion;
    this.major = major;
    this.minor = minor;
    this.fix = fix;
    this.suffix = suffix;
  }

  /**
   * get the version string of artifact
   * @return artifact version string
   */
  @Nullable
  public String getVersion() {
    return version;
  }

  /**
   * get the major version of artifact
   * @return major version of artifact
   */
  @Nullable
  public Integer getMajor() {
    return major;
  }

  /**
   * get the minor version of artifact
   * @return minor version of artifact
   *
   */
  @Nullable
  public Integer getMinor() {
    return minor;
  }

  /**
   * get the fix version of artifact
   * @return fix version of artifact
   *
   */
  @Nullable
  public Integer getFix() {
    return fix;
  }

  /**
   * get the artifact version suffix
   * @return artifact version suffix
   */
  @Nullable
  public String getSuffix() {
    return suffix;
  }

  /**
   * get if artifact version is a snapshot version
   * @return true if artifact version is snapshot, false otherwise
   */
  public boolean isSnapshot() {
    return suffix != null && !suffix.isEmpty() && suffix.toLowerCase().startsWith("snapshot");
  }

  @Override
  public int compareTo(ArtifactVersion other) {
    int cmp = compare(major, other.major);
    if (cmp != 0) {
      return cmp;
    }

    cmp = compare(minor, other.minor);
    if (cmp != 0) {
      return cmp;
    }

    cmp = compare(fix, other.fix);
    if (cmp != 0) {
      return cmp;
    }

    // All numerical part of the version are the same, compare the suffix.
    // A special case is no suffix is "greater" than with suffix. This is usually true (e.g. release > snapshot)
    if (suffix == null) {
      return other.suffix == null ? 0 : 1;
    }
    return other.suffix == null ? -1 : suffix.compareTo(other.suffix);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ArtifactVersion that = (ArtifactVersion) o;
    return !(version != null ? !version.equals(that.version) : that.version != null);
  }

  @Override
  public int hashCode() {
    return version != null ? version.hashCode() : 0;
  }

  @Override
  public String toString() {
    return version;
  }

  /**
   * Compares two {@link Comparable}s that can be null. Returns -1, 0, 1 if first is smaller, equal, larger than second,
   * based on comparison defined by the {@link Comparable}.
   * The {@code null} value is smaller than any non-null value and only equals to {@code null}.
   */
  private <T extends Comparable<T>> int compare(@Nullable T first, @Nullable T second) {
    if (first == null && second == null) {
      return 0;
    }
    if (first == null) {
      return -1;
    }
    if (second == null) {
      return 1;
    }
    return first.compareTo(second);
  }

  /**
   * Parses the given string as integer. If failed, returns {@code null}.
   */
  @Nullable
  private Integer valueOf(@Nullable String str) {
    try {
      if (str == null) {
        return null;
      }
      return Integer.valueOf(str);
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
