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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BashCommand {

  /** Allowlist of executables that may be invoked. */
  private static final Set<String> ALLOWED_COMMANDS = Set.of("convertVideo");

  /** Every argument (including the executable) must match this safe pattern. */
  private static final Pattern SAFE_ARG_PATTERN = Pattern.compile("^[a-zA-Z0-9_.\\-/ ]+$");

  /**
   * Execute a command using ProcessBuilder with an argument list. Each argument is validated
   * against an allowlist/safe-character pattern before execution to prevent OS command injection.
   *
   * @param command the command and its arguments as a list
   * @return the command output, or null on failure
   * @throws IllegalArgumentException if the command or any argument fails validation
   */
  public String executeCommand(List<String> command) throws IOException {
    // Validate command list before execution
    List<String> validatedCommand = validateCommand(command);

    BufferedReader b = null;
    StringBuilder output;
    log.info("Executing command: {}", validatedCommand);
    ProcessBuilder pb = new ProcessBuilder(validatedCommand);
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
      log.error("Failed to execute command: {}", command);
      e.printStackTrace();
    } finally {
      if (b != null) {
        b.close();
      }
    }
    return null;
  }

  /**
   * Validates that the command executable is in the allowlist and every argument contains only safe
   * characters. Returns a new list so the ProcessBuilder receives a locally-constructed value.
   */
  private List<String> validateCommand(List<String> command) {
    if (command == null || command.isEmpty()) {
      throw new IllegalArgumentException("Command list must not be null or empty");
    }
    String executable = command.get(0);
    if (!ALLOWED_COMMANDS.contains(executable)) {
      throw new IllegalArgumentException("Command not allowed: " + executable);
    }
    List<String> validated = new ArrayList<>(command.size());
    for (String arg : command) {
      if (arg == null || !SAFE_ARG_PATTERN.matcher(arg).matches()) {
        throw new IllegalArgumentException("Invalid command argument");
      }
      validated.add(arg);
    }
    return validated;
  }
}
