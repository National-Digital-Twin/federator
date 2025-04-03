// SPDX-License-Identifier: Apache-2.0
// © Crown Copyright 2025. This work has been developed by the National Digital Twin Programme
// and is legally attributed to the Department for Business and Trade (UK) as the governing entity.

/*
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

/**
 * JShell Script to create new hash values given a password (String) and a salt (String)
 *
 * The generated hash value is to be placed in the servers configuration file (access.json) within the "hashed_key" field
 * The salt value is to be placed in the servers configuration file (access.json) within the "salt" field
 * The password is to be placed in the clients configuration file (connection-configuration.json) within the ""key"" field
 *
 * This hash value is used to authenticate a client to the server
 * The hash value is calculated using the SHA3-256 hashing algorithm
 * The hash value is calculated by concatenating the salt and the password, then hashing the result
 * The salt is a string value that is unique to the client
 *
 * To use:
 *   - edit this file and replace the values assigned to the 'salt' and 'passwordClient' variables
 *   - in a command prompt, load the script into jshell using 'jshell createClientHashValueJshell.java'
 *   - the jshell will compile and run the source code
 *   - enter the jshell command '/var' to see the contents of the variables
 *   - the calculated hash value will be shown in the variable 'clientShaHashValue'
 *   - to exit the jshell enter '/exit'
 */

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public static final String HASHING_ALGORITHM = "SHA3-256";
MessageDigest digest = MessageDigest.getInstance(HASHING_ALGORITHM);

private static String bytesToHex(byte[] hash) {
  StringBuilder hexString = new StringBuilder(2 * hash.length);
  for (byte b : hash) {
    String hex = Integer.toHexString(0xff & b);
    if (hex.length() == 1) {
      hexString.append('0');
    }
    hexString.append(hex);
  }
  return hexString.toString();
}

String salt="<REPLACE_WITH_A_SALT_VALUE>";
String passwordClient="<REPLACE_WITH_A_PASSWORD>";
String saltedPasswordClient=passwordClient+salt;
byte[] hashBytesClient = digest.digest(saltedPasswordClient.getBytes(StandardCharsets.UTF_8));
String clientShaHashValue = bytesToHex(hashBytesClient);


