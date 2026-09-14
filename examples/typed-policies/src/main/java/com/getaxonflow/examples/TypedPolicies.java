// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.examples;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getaxonflow.sdk.AxonFlow;
import com.getaxonflow.sdk.AxonFlowConfig;
import com.getaxonflow.sdk.exceptions.TypedPolicyRefusalException;
import com.getaxonflow.sdk.types.policies.TypedPolicyTypes.ActiveTypedPolicy;
import com.getaxonflow.sdk.types.policies.TypedPolicyTypes.AuthoringFinding;
import com.getaxonflow.sdk.types.policies.TypedPolicyTypes.TemplateOmissionReport;
import com.getaxonflow.sdk.types.policies.TypedPolicyTypes.TypedAuthoringEdition;
import com.getaxonflow.sdk.types.policies.TypedPolicyTypes.TypedPolicyPublication;
import com.getaxonflow.sdk.types.policies.TypedPolicyTypes.TypedPolicyValidation;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Typed policy authoring against a running AxonFlow v11 platform.
 *
 * <p>A v11 platform authors policy as a typed document: validated, published as a signed artifact
 * pinned by its digest, and promoted to active. This example reads what the deployment may author,
 * validates a document and prints every finding, and shows the document in force. It publishes and
 * activates only when {@code AXONFLOW_TYPED_POLICY_PUBLISH=1}, because that changes the
 * organization's active policy.
 *
 * <p>Before it activates, it prints the publication's template-omission report: activating a
 * document that omits the organization template's controls removes those controls for the
 * organization. The default document is a minimal example, not a starting point for production:
 * it omits all of them. A publication or activation that was asked for and refused prints the
 * platform's reason and findings, and fails the run.
 *
 * <p>After a document with an organization-scope constraint is activated, a decide that does not
 * supply the attribute the constraint conditions on is denied fail-closed with reasons
 * ["unknown_constraint"]; supply the attribute or run this example on a fresh stack. From v11.0.0
 * the deny's first reason is that code, followed by one naming each constraint it could not
 * evaluate and the attribute it needed (getaxonflow/axonflow-enterprise#4247). The default document
 * is such a document, so run the pep-handshake example first.
 *
 * <p>{@code AXONFLOW_TYPED_POLICY_BODY} names a JSON file holding {@code {"document": ...,
 * "fixtures": [...]}}. Without it the example reads the repository's {@code
 * tests/fixtures/typed_policy_publish_body.json}, which its build puts on the classpath, so it runs
 * from any directory. On an edition with separation of duties, see the README's Typed policy
 * authoring section for how a publication is approved.
 *
 * <pre>
 * mvn -q -DskipTests -DskipUnitTests=true install   # from the repository root: the SDK on the local classpath
 * AXONFLOW_AGENT_URL=http://localhost:8080 AXONFLOW_TYPED_POLICY_PUBLISH=1 \
 *   mvn -q -f examples/typed-policies/pom.xml compile exec:java
 * </pre>
 *
 * <p>Exits non-zero if a step fails, so it is usable as a smoke test.
 */
public final class TypedPolicies {

  /** The default document, on the classpath. */
  static final String DEFAULT_BODY = "/typed_policy_publish_body.json";

  private static int failures;

  private TypedPolicies() {}

  @FunctionalInterface
  private interface Step {
    void run() throws Exception;
  }

  private static void step(String name, Step body) {
    System.out.println("\n=== " + name + " ===");
    try {
      body.run();
      System.out.println("ok");
    } catch (Exception e) {
      System.out.println("FAILED: " + e.getClass().getSimpleName() + ": " + e.getMessage());
      failures++;
    }
  }

  private static void print(List<AuthoringFinding> findings) {
    if (findings == null) {
      return;
    }
    for (AuthoringFinding f : findings) {
      System.out.println(
          "  "
              + f.getSeverity()
              + " "
              + f.getCode()
              + " "
              + f.getPolicyId()
              + ": "
              + f.getDetail());
    }
  }

  /** Prints a refusal: the platform's reason and any findings that refused it. */
  private static void printRefusal(String what, TypedPolicyRefusalException refusal) {
    System.out.println(
        what
            + ": HTTP "
            + refusal.getStatus()
            + " "
            + refusal.getReason()
            + ": "
            + refusal.getMessage());
    print(refusal.getFindings());
  }

  /**
   * Prints which of the organization template's controls the document omits: activating it removes
   * them for the organization.
   */
  private static void printTemplateOmissions(TypedPolicyPublication published) {
    TemplateOmissionReport report = published.getTemplateOmissions();
    if (report != null) {
      System.out.println(
          "template omissions: "
              + report.getOmitted().size()
              + " of "
              + report.getOf()
              + " template controls: "
              + String.join(", ", report.getOmitted()));
    } else if (published.getTemplateOmissionsUnavailable() != null) {
      System.out.println(
          "template omissions: unavailable: " + published.getTemplateOmissionsUnavailable());
    } else {
      System.out.println("template omissions: none");
    }
  }

  /** The body {@code path} names, or the default document on the classpath when it is unset. */
  static Map<String, Object> readBody(String path) throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    TypeReference<Map<String, Object>> type = new TypeReference<Map<String, Object>>() {};
    if (path != null && !path.isEmpty()) {
      return mapper.readValue(Files.readAllBytes(Paths.get(path)), type);
    }
    try (InputStream in = TypedPolicies.class.getResourceAsStream(DEFAULT_BODY)) {
      if (in == null) {
        throw new IOException(
            DEFAULT_BODY
                + " is not on the classpath: build the example with its pom, or set"
                + " AXONFLOW_TYPED_POLICY_BODY");
      }
      return mapper.readValue(in, type);
    }
  }

  public static void main(String[] args) throws Exception {
    String endpoint = System.getenv().getOrDefault("AXONFLOW_AGENT_URL", "http://localhost:8080");
    Map<String, Object> body = readBody(System.getenv("AXONFLOW_TYPED_POLICY_BODY"));
    @SuppressWarnings("unchecked")
    Map<String, Object> document = (Map<String, Object>) body.get("document");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> fixtures = (List<Map<String, Object>>) body.get("fixtures");

    AxonFlowConfig.Builder config = AxonFlowConfig.builder().endpoint(endpoint);
    String clientId = System.getenv("AXONFLOW_CLIENT_ID");
    if (clientId != null && !clientId.isEmpty()) {
      config.clientId(clientId).clientSecret(System.getenv("AXONFLOW_CLIENT_SECRET"));
    }
    try (AxonFlow client = AxonFlow.create(config.build())) {
      AxonFlow.TypedPoliciesNamespace typed = client.typedPolicies();

      // What this deployment may author: consult it before publishing rather than learning the
      // edition's boundary from a refusal.
      step(
          "what this deployment may author",
          () -> {
            TypedAuthoringEdition edition = typed.edition();
            System.out.println(
                "root="
                    + edition.getRoot()
                    + " max_documents="
                    + edition.getMaxDocuments()
                    + " persistence="
                    + edition.getPersistence());
          });

      // Validation reports every finding. A refused document is still a successful validation: read
      // isSuccess() and the findings, do not expect an exception.
      step(
          "validate the document",
          () -> {
            TypedPolicyValidation validation = typed.validate(document, fixtures);
            System.out.println("success=" + validation.isSuccess());
            print(validation.getFindings());
          });

      if ("1".equals(System.getenv("AXONFLOW_TYPED_POLICY_PUBLISH"))) {
        step(
            "publish and activate",
            () -> {
              TypedPolicyPublication published;
              try {
                published = typed.publish(document, fixtures);
              } catch (TypedPolicyRefusalException refusal) {
                // A refusal carries the platform's reason and, for a refused document, the findings
                // that refused it. Publishing was asked for, so a refusal fails the step.
                printRefusal("refused", refusal);
                throw refusal;
              }
              System.out.println(
                  "published " + published.getDigest() + " (version " + published.getVersion() + ")");
              // Before activating: activating removes, for the organization, every template
              // control the document omits.
              printTemplateOmissions(published);
              try {
                typed.activate(published.getDigest(), "examples/typed-policies");
              } catch (TypedPolicyRefusalException refusal) {
                // Activation promotes: a document whose version does not advance past the active
                // one is refused. Activating was asked for, so a refusal fails the step.
                printRefusal("activation refused", refusal);
                throw refusal;
              }
              System.out.println("activated");
            });
      }

      // The document in force is returned as the exact bytes that were signed.
      step(
          "the document in force",
          () -> {
            Optional<ActiveTypedPolicy> active = typed.active();
            if (!active.isPresent()) {
              System.out.println("nothing is active");
              return;
            }
            Object metadata = active.get().getDocument().get("metadata");
            System.out.println(
                active.get().getSource().length() + " signed characters; metadata=" + metadata);
          });
    }

    if (failures > 0) {
      System.out.println("\n" + failures + " step(s) failed");
      System.exit(1);
    }
  }
}
