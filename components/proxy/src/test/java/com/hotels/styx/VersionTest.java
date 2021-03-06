/*
  Copyright (C) 2013-2018 Expedia Inc.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */
package com.hotels.styx;

import org.testng.annotations.Test;

import java.util.Optional;

import static com.hotels.styx.Version.newVersion;
import static com.hotels.styx.Version.readVersionFrom;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class VersionTest {

    @Test
    public void readBuildInfoFromTheSpecifiedPath() {
        Version version = readVersionFrom("/version.json");
        assertThat(version.releaseTag(), containsString("STYX"));
        assertThat(version.buildNumber(), is(Optional.of(125)));
    }

    @Test
    public void willCreateAnEmptyBuildInfoIfFailsToRead() {
        assertThat(readVersionFrom("version.txt"), is(newVersion()));
    }

}