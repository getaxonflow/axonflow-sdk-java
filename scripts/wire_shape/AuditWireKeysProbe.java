/*
 * Copyright 2026 AxonFlow
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Wire-key introspection probe for wire-shape Gate 5 (audit-surface binding, #3254).
 *
 * <p>Run by scripts/wire_shape/validate.py in java source-file mode against the COMPILED SDK
 * classes ({@code target/classes}) plus the resolved dependency classpath. For every
 * fully-qualified class name passed as an argument it asks Jackson itself - the same library that
 * puts these types on the wire - for the full set of wire property names, as the union of the
 * serialization and deserialization bean descriptions, and prints one JSON object mapping simple
 * class name to sorted wire keys.
 *
 * <p>Why introspection instead of source-regex discovery: a regex over the source cannot resolve a
 * constant-valued annotation ({@code @JsonProperty(SOME_CONSTANT)}) and cannot see Jackson's
 * getter auto-detection (an unannotated public {@code getFoo()} serializes {@code foo} with no
 * {@code @JsonProperty} anywhere). Both were demonstrated as Gate 5 bypasses in review. The
 * compiled-class view resolves constants (the annotation value is a resolved string at bytecode
 * level) and applies the exact property-discovery rules the production {@code ObjectMapper} uses,
 * so what this probe reports IS what can appear on the wire.
 *
 * <p>Failure behavior: any unresolvable input (class not found, introspection error) prints the
 * cause to stderr and exits 2. The caller treats any non-zero exit as an unresolvable binding and
 * FAILS the gate - never skips.
 */
public final class AuditWireKeysProbe {

  private AuditWireKeysProbe() {}

  public static void main(String[] args) {
    if (args.length == 0) {
      System.err.println("usage: AuditWireKeysProbe <fully-qualified-class>...");
      System.exit(2);
    }
    try {
      ObjectMapper mapper = new ObjectMapper();
      TreeMap<String, TreeSet<String>> result = new TreeMap<>();
      for (String fqcn : args) {
        Class<?> cls = Class.forName(fqcn);
        JavaType type = mapper.constructType(cls);
        TreeSet<String> keys = new TreeSet<>();
        BeanDescription ser = mapper.getSerializationConfig().introspect(type);
        for (BeanPropertyDefinition p : ser.findProperties()) {
          keys.add(p.getName());
        }
        BeanDescription deser = mapper.getDeserializationConfig().introspect(type);
        for (BeanPropertyDefinition p : deser.findProperties()) {
          keys.add(p.getName());
        }
        result.put(cls.getSimpleName(), keys);
      }
      System.out.println(mapper.writeValueAsString(result));
    } catch (Throwable t) {
      System.err.println("AuditWireKeysProbe FAILED: " + t);
      System.exit(2);
    }
  }
}
