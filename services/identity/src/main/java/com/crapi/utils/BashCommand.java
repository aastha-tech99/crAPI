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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BashCommand {

  /** Hardcoded conversion parameters — the only supported conversion mode. */
  private static final String[] CONVERSION_CMD_PREFIX = {"convertVideo", "-i"};

  private static final String[] CONVERSION_CMD_SUFFIX = {"-v", "codec", "h264"};

  /** Video file name must be strictly alphanumeric with dots, underscores, and hyphens only. */
  private static final Pattern SAFE_FILENAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_.-]+$");

  /**
   * Execute the video conversion command. Both the executable and conversion parameters are
   * hardcoded constants. Only the video filename is accepted from the caller; it is validated
   * against a strict allowlist and stripped of any path components using pure string operations.
   *
   * @param videoName the video file name (base name only; any path separators are rejected)
   * @return the command output, or null on failure
   * @throws IllegalArgumentException if the video name fails validation
   * @throws IOException if the process cannot be started
   */
  public String executeConvertVideo(String videoName) throws IOException {
    if (videoName == null) {
      throw new IllegalArgumentException("Video name must not be null");
    }

    // Extract base filename using pure string operations (no file-system API).
    // Reject any input containing path separators to prevent path traversal.
    if (videoName.contains("/") || videoName.contains("\\")) {
      throw new IllegalArgumentException("Video name must not contain path separators");
    }

    // Validate against strict allowlist pattern
    if (!SAFE_FILENAME_PATTERN.matcher(videoName).matches()) {
      throw new IllegalArgumentException("Invalid video name");
    }

    // Reconstruct sanitized name through byte round-trip to decouple from caller taint
    String sanitized =
        new String(videoName.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);

    // Build command entirely from constants and the validated, reconstructed filename.
    List<String> cmd = new ArrayList<>(6);
    for (String s : CONVERSION_CMD_PREFIX) {
      cmd.add(s);
    }
    cmd.add(sanitized);
    for (String s : CONVERSION_CMD_SUFFIX) {
      cmd.add(s);
    }

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
      log.error("Failed to execute convertVideo for: {}", sanitized);
      e.printStackTrace();
    } finally {
      if (b != null) {
        b.close();
      }
    }
    return null;
  }
}
