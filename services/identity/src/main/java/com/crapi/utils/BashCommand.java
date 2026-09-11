/*
 * Licensed under the Apache License, Version 2.0 (the “License”);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an “AS IS” BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.crapi.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BashCommand {

  /** Pattern matching shell metacharacters that must not appear in command arguments. */
  private static final Pattern SHELL_METACHAR_PATTERN =
      Pattern.compile("[;&|`$(){}\\[\\]<>!\\\\\"'*?~#\n\r]");

  /**
   * Execute a command safely using ProcessBuilder with an explicit argument list, avoiding shell
   * interpretation. Each argument is validated to reject shell metacharacters.
   *
   * @param commandArgs the command executable and its arguments as separate strings
   * @return the stdout output of the command
   */
  public String executeBashCommand(List<String> commandArgs) throws IOException {
    if (commandArgs == null || commandArgs.isEmpty()) {
      throw new IllegalArgumentException("Command arguments must not be null or empty");
    }
    for (String arg : commandArgs) {
      if (arg == null || SHELL_METACHAR_PATTERN.matcher(arg).find()) {
        throw new IllegalArgumentException("Invalid characters in command argument");
      }
    }

    log.info("Executing command:\n   {}", commandArgs);
    BufferedReader b = null;
    StringBuilder output;
    try {
      ProcessBuilder pb = new ProcessBuilder(commandArgs);
      pb.redirectErrorStream(true);
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
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      log.error("Failed to execute command: {}", commandArgs);
      e.printStackTrace();
    } finally {
      if (b != null) {
        b.close();
      }
    }
    return null;
  }
}
