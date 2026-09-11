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
import java.util.Map;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BashCommand {

  /** Allowlist mapping permitted command names to their executable paths. */
  private static final Map<String, String> ALLOWED_COMMANDS =
      Map.of("convertVideo", "convertVideo");

  /** Pattern matching shell metacharacters that must not appear in command arguments. */
  private static final Pattern SHELL_METACHAR_PATTERN =
      Pattern.compile("[;&|`$(){}\\[\\]<>!\\\\\"'*?~#\n\r]");

  /**
   * Execute an allowed command safely using ProcessBuilder. The command name is resolved from a
   * fixed allowlist to prevent command injection — the resolved value is a compile-time constant,
   * not the caller-supplied string. Each argument is validated to reject shell metacharacters.
   *
   * @param commandName the command name (must match an entry in the allowlist)
   * @param args the command arguments as separate strings
   * @return the stdout output of the command
   */
  public String executeAllowedCommand(String commandName, List<String> args) throws IOException {
    // Resolve command from the constant allowlist — this breaks taint propagation
    // because the returned value is a compile-time map constant, not the caller input.
    String resolvedCommand = ALLOWED_COMMANDS.get(commandName);
    if (resolvedCommand == null) {
      throw new IllegalArgumentException("Command not in allowlist: " + commandName);
    }
    if (args == null) {
      throw new IllegalArgumentException("Command arguments must not be null");
    }
    for (String arg : args) {
      if (arg == null || SHELL_METACHAR_PATTERN.matcher(arg).find()) {
        throw new IllegalArgumentException("Invalid characters in command argument");
      }
    }

    // Build command line from the allowlisted constant and validated arguments
    List<String> commandLine = new ArrayList<>();
    commandLine.add(resolvedCommand);
    commandLine.addAll(args);

    log.info("Executing command:\n   {}", commandLine);
    BufferedReader b = null;
    StringBuilder output;
    try {
      ProcessBuilder pb = new ProcessBuilder(commandLine);
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
      log.error("Failed to execute command: {}", commandLine);
      e.printStackTrace();
    } finally {
      if (b != null) {
        b.close();
      }
    }
    return null;
  }
}
