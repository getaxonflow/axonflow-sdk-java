// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getaxonflow.sdk.AxonFlow;
import com.getaxonflow.sdk.AxonFlowConfig;
import com.getaxonflow.sdk.exceptions.TypedPolicyRefusalException;
import com.getaxonflow.sdk.types.policies.TypedPolicyTypes.ActiveTypedPolicy;
import com.getaxonflow.sdk.types.policies.TypedPolicyTypes.AuthoringFinding;
import com.getaxonflow.sdk.types.policies.TypedPolicyTypes.TypedAuthoringEdition;
import com.getaxonflow.sdk.types.policies.TypedPolicyTypes.TypedPolicyActivation;
import com.getaxonflow.sdk.types.policies.TypedPolicyTypes.TypedPolicyPublication;
import com.getaxonflow.sdk.types.policies.TypedPolicyTypes.TypedPolicySystemCorpus;
import com.getaxonflow.sdk.types.policies.TypedPolicyTypes.TypedPolicyValidation;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Real-stack proof of typed policy authoring through the SDK. It drives {@code
 * client.typedPolicies()} against a real agent and orchestrator and asserts, on a fresh stack:
 *
 * <ol>
 *   <li>Nothing is active yet: {@code active()} is empty from the platform's 404.
 *   <li>{@code edition()} reports the deployment's boundary, and {@code system()} the shipped
 *       controls with their digest.
 *   <li>The document the platform's own route test proves publishable validates clean, publishes to
 *       a digest, and activates.
 *   <li>{@code active()} returns that document as the exact signed source, with the AUTHOR
 *       overwritten by the platform: the document deliberately names someone-else, and the platform
 *       signs the caller the agent resolved.
 *   <li>Activating the same digest again is a typed 409 ({@code activation_refused}).
 *   <li>Publishing with no fixtures is a typed 422 ({@code publication_refused}) whose message names
 *       the missing fixtures.
 *   <li>A document naming an action the registry does not hold validates with the platform's
 *       rejecting finding ({@code ACTION_NOT_REGISTERED}), and publishing it is a typed 422 ({@code
 *       document_refused}) carrying that finding.
 * </ol>
 *
 * <p>The document is {@code tests/fixtures/typed_policy_publish_body.json}, marshalled by the
 * platform's own types, with its {@code document_id} made unique per run. Run it against a FRESH
 * community stack; see README.md.
 */
public class TypedPolicyAuthoringTest {

  static final String AGENT = env("AXONFLOW_AGENT_URL", "http://localhost:8080");
  static final List<String> FAILURES = new ArrayList<>();
  static final ObjectMapper MAPPER = new ObjectMapper();
  static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};
  static final TypeReference<List<Map<String, Object>>> LIST_OF_MAPS = new TypeReference<>() {};

  static String env(String name, String fallback) {
    String v = System.getenv(name);
    return v == null || v.isEmpty() ? fallback : v;
  }

  static void check(boolean ok, String description) {
    System.out.println((ok ? "PASS: " : "FAIL: ") + description);
    if (!ok) {
      FAILURES.add(description);
    }
  }

  static boolean hasFinding(List<AuthoringFinding> findings, String code, String severity, String policyId) {
    return findings.stream()
        .anyMatch(
            f ->
                code.equals(f.getCode())
                    && severity.equals(f.getSeverity())
                    && policyId.equals(f.getPolicyId()));
  }

  @SuppressWarnings("unchecked")
  static Map<String, Object> member(Map<String, Object> m, String key) {
    return (Map<String, Object>) m.get(key);
  }

  public static void main(String[] args) throws Exception {
    System.out.println("agent: " + AGENT);
    JsonNode body = MAPPER.readTree(Files.readAllBytes(Paths.get("tests/fixtures/typed_policy_publish_body.json")));
    Map<String, Object> document = MAPPER.convertValue(body.get("document"), MAP);
    List<Map<String, Object>> fixtures = MAPPER.convertValue(body.get("fixtures"), LIST_OF_MAPS);
    byte[] suffix = new byte[6];
    new SecureRandom().nextBytes(suffix);
    StringBuilder hex = new StringBuilder();
    for (byte b : suffix) {
      hex.append(String.format("%02x", b));
    }
    String documentId = "sdk-java-e2e-" + hex;
    member(document, "metadata").put("document_id", documentId);

    AxonFlow client =
        AxonFlow.create(
            AxonFlowConfig.builder()
                .endpoint(AGENT)
                .clientId(env("AXONFLOW_CLIENT_ID", "runtime-e2e"))
                .clientSecret(env("AXONFLOW_CLIENT_SECRET", "runtime-e2e-secret"))
                .build());
    AxonFlow.TypedPoliciesNamespace typed = client.typedPolicies();

    System.out.println("== nothing active yet");
    check(typed.active().isEmpty(), "active() is empty before any activation");

    System.out.println("== edition and system");
    TypedAuthoringEdition edition = typed.edition();
    System.out.println(
        "  edition: catalog="
            + edition.getCatalog()
            + " root="
            + edition.getRoot()
            + " max_documents="
            + edition.getMaxDocuments()
            + " persistence="
            + edition.getPersistence()
            + " constructs.edition="
            + (edition.getConstructs() == null ? null : edition.getConstructs().getEdition()));
    check(edition.isSuccess() && "organization".equals(edition.getRoot()), "edition() reports the root");
    check(edition.getConstructs() != null, "edition() reports the construct boundary");
    TypedPolicySystemCorpus system = typed.system();
    System.out.println(
        "  system: root="
            + system.getRoot()
            + " version="
            + system.getVersion()
            + " digest="
            + system.getDigest()
            + " controls="
            + system.getControls().size()
            + " assurance_counts="
            + system.getAssuranceCounts());
    check(
        system.getDigest() != null && !system.getDigest().isEmpty() && !system.getControls().isEmpty(),
        "system() returns the shipped corpus");

    System.out.println("== validate, publish, activate");
    TypedPolicyValidation validation = typed.validate(document, fixtures);
    System.out.println("  validate: success=" + validation.isSuccess() + " findings=" + validation.getFindings());
    check(validation.isSuccess(), "the document validates clean");
    TypedPolicyPublication published = typed.publish(document, fixtures);
    System.out.println("  publish: digest=" + published.getDigest() + " version=" + published.getVersion());
    check(published.getDigest() != null && !published.getDigest().isEmpty(), "publish() returns the artifact digest");
    TypedPolicyActivation activation = typed.activate(published.getDigest(), "sdk-java runtime proof");
    System.out.println("  activate: success=" + activation.isSuccess() + " activation=" + activation.getActivation());
    check(activation.isSuccess(), "activate() promotes the digest");

    System.out.println("== the document in force");
    Optional<ActiveTypedPolicy> active = typed.active();
    check(active.isPresent(), "active() returns the document in force");
    if (active.isPresent()) {
      Map<String, Object> metadata = member(active.get().getDocument(), "metadata");
      Map<String, Object> author = member(metadata, "author");
      System.out.println("  active: document_id=" + metadata.get("document_id") + " author=" + author);
      check(documentId.equals(metadata.get("document_id")), "active() is the document just activated");
      check(
          MAPPER.readValue(active.get().getSource(), MAP).equals(active.get().getDocument()),
          "its source is the signed source the document parses from");
      check(
          author != null && !"someone-else".equals(author.get("local")),
          "the platform signed the caller as author, not the name in the request");
    }

    System.out.println("== typed refusals");
    try {
      typed.activate(published.getDigest(), null);
      check(false, "re-activating the active digest is refused");
    } catch (TypedPolicyRefusalException e) {
      System.out.println("  re-activate: status=" + e.getStatus() + " reason=" + e.getReason() + " error=" + e.getMessage());
      check(
          e.getStatus() == 409 && "activation_refused".equals(e.getReason()),
          "re-activating the active digest is a typed 409 activation_refused");
    }
    try {
      typed.publish(document, List.of());
      check(false, "publishing with no fixtures is refused");
    } catch (TypedPolicyRefusalException e) {
      System.out.println(
          "  publish without fixtures: status=" + e.getStatus() + " reason=" + e.getReason() + " error=" + e.getMessage());
      check(
          e.getStatus() == 422
              && "publication_refused".equals(e.getReason())
              && e.getMessage().contains("declares no fixtures"),
          "publishing with no fixtures is a typed 422 publication_refused naming the cause");
    }

    System.out.println("== a document the save-time checks reject");
    Map<String, Object> unregistered = MAPPER.convertValue(document, MAP);
    member(unregistered, "metadata").put("document_id", documentId + "-unregistered");
    @SuppressWarnings("unchecked")
    Map<String, Object> policy =
        (Map<String, Object>) ((List<Object>) member(unregistered, "policy").get("policies")).get(0);
    @SuppressWarnings("unchecked")
    Map<String, Object> action =
        (Map<String, Object>) ((List<Object>) member(policy, "actions").get("actions")).get(0);
    action.put("local", "tool.not_registered");
    TypedPolicyValidation rejected = typed.validate(unregistered, fixtures);
    System.out.println("  validate: success=" + rejected.isSuccess() + " findings=" + rejected.getFindings());
    check(
        !rejected.isSuccess() && hasFinding(rejected.getFindings(), "ACTION_NOT_REGISTERED", "reject", "grant.refund"),
        "validate() answers an unregistered action with the platform's rejecting finding");
    try {
      typed.publish(unregistered, fixtures);
      check(false, "publishing a document the save-time checks reject is refused");
    } catch (TypedPolicyRefusalException e) {
      System.out.println("  publish: status=" + e.getStatus() + " reason=" + e.getReason() + " findings=" + e.getFindings());
      check(
          e.getStatus() == 422
              && "document_refused".equals(e.getReason())
              && hasFinding(e.getFindings(), "ACTION_NOT_REGISTERED", "reject", "grant.refund"),
          "publishing it is a typed 422 document_refused carrying that finding");
    }

    client.close();
    if (!FAILURES.isEmpty()) {
      System.out.println("\nFAIL: typed_policies (" + FAILURES.size() + " assertion(s))");
      System.exit(1);
    }
    System.out.println("\nPASS: typed_policies");
  }
}
