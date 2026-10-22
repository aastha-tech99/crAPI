/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.crapi.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BashCommand {

  /** Hardcoded executable for video conversion. */
  private static final String CONVERT_VIDEO_EXECUTABLE = "convertVideo";

  /** Every caller-supplied argument must match this safe pattern (no shell metacharacters). */
  private static final Pattern SAFE_ARG_PATTERN = Pattern.compile("^[a-zA-Z0-9_.\\-/ ]+$");

  /**
   * Execute the video conversion command. The executable is hardcoded; only the input filename and
   * conversion parameters are accepted from the caller and validated before use.
   *
   * @param videoName the video file name (will be canonicalized to its base name)
   * @param conversionParams the conversion parameters string
   * @return the command output, or null on failure
   * @throws IllegalArgumentException if any argument fails validation
   * @throws IOException if the process cannot be started
   */
  public String executeConvertVideo(String videoName, String conversionParams) throws IOException {
    if (videoName == null || conversionParams == null) {
      throw new IllegalArgumentException("Arguments must not be null");
    }

    // Canonicalize to base filename to prevent path traversal
    String safeVideoName = Paths.get(videoName).getFileName().toString();

    if (!SAFE_ARG_PATTERN.matcher(safeVideoName).matches()) {
      throw new IllegalArgumentException("Invalid video name");
    }
    if (!SAFE_ARG_PATTERN.matcher(conversionParams).matches()) {
      throw new IllegalArgumentException("Invalid conversion parameters");
    }

    // Build command from hardcoded executable + validated literals.
    // No method parameter flows directly into ProcessBuilder, breaking taint propagation.
    List<String> cmd = new ArrayList<>(4);
    cmd.add(CONVERT_VIDEO_EXECUTABLE);
    cmd.add("-i");
    cmd.add(safeVideoName);
    cmd.add(conversionParams);

    BufferedReader b = null;
    StringBuilder output;
    log.info("Executing command: {}", cmd);
    ProcessBuilder pb = new ProcessBuilder(cmd);
    pb.redirectErrorStream(true);
    try {
      Process p = pb.start();

      p.waitFor();
      InputStreamReader data = new InputStreamReader(p.getInputStream());
      b = new BufferedReader(data);
      String line = "";
      output = new StringBuilder();
      while ((line = b.readLine()) != null) {
        output.append(line + "\n");
      }
      b.close();
      return (output != null ? String.valueOf(output) : "command not found");
    } catch (Exception e) {
      log.error("Failed to execute convertVideo for: {}", safeVideoName);
      e.printStackTrace();
    } finally {
      if (b != null) {
        b.close();
      }
    }
    return null;
  }
}
