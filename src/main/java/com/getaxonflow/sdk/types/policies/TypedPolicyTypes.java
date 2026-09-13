// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.types.policies;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The types of typed policy authoring (platform v11.0.0), reached through {@code
 * AxonFlow.typedPolicies()}.
 *
 * <p>{@link EditionConstructReport}, {@link AuthoringFinding} and {@link
 * TypedAuthoringDocumentRequest} carry the spec's schema names, so the wire-shape contract
 * registers them. The platform marshals a collection it holds as a nil Go slice or map as JSON null
 * (a clean validation answers {@code "findings": null}), and every collection here reads null as
 * empty.
 */
public final class TypedPolicyTypes {

  private TypedPolicyTypes() {}

  private static <T> List<T> list(List<T> in) {
    return in == null ? Collections.emptyList() : Collections.unmodifiableList(in);
  }

  private static <K, V> Map<K, V> map(Map<K, V> in) {
    return in == null ? Collections.emptyMap() : Collections.unmodifiableMap(in);
  }

  /** What this edition may spend (the spec's {@code EditionConstructReport}). */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class EditionConstructReport {
    @JsonProperty("edition")
    private final String edition;

    @JsonProperty("obligation_families")
    private final List<String> obligationFamilies;

    @JsonProperty("attribute_namespaces")
    private final List<String> attributeNamespaces;

    @JsonProperty("group_scope")
    private final Boolean groupScope;

    @JsonProperty("separation_of_duties")
    private final Boolean separationOfDuties;

    @JsonProperty("tier_established")
    private final Boolean tierEstablished;

    @JsonProperty("reserved")
    private final List<String> reserved;

    @JsonCreator
    public EditionConstructReport(
        @JsonProperty("edition") String edition,
        @JsonProperty("obligation_families") List<String> obligationFamilies,
        @JsonProperty("attribute_namespaces") List<String> attributeNamespaces,
        @JsonProperty("group_scope") Boolean groupScope,
        @JsonProperty("separation_of_duties") Boolean separationOfDuties,
        @JsonProperty("tier_established") Boolean tierEstablished,
        @JsonProperty("reserved") List<String> reserved) {
      this.edition = edition;
      this.obligationFamilies = list(obligationFamilies);
      this.attributeNamespaces = list(attributeNamespaces);
      this.groupScope = groupScope;
      this.separationOfDuties = separationOfDuties;
      this.tierEstablished = tierEstablished;
      this.reserved = list(reserved);
    }

    /** Returns community, evaluation or enterprise. */
    public String getEdition() {
      return edition;
    }

    public List<String> getObligationFamilies() {
      return obligationFamilies;
    }

    public List<String> getAttributeNamespaces() {
      return attributeNamespaces;
    }

    public Boolean getGroupScope() {
      return groupScope;
    }

    public Boolean getSeparationOfDuties() {
      return separationOfDuties;
    }

    public Boolean getTierEstablished() {
      return tierEstablished;
    }

    /** Returns the constructs withheld for want of an edition ruling, not by one. */
    public List<String> getReserved() {
      return reserved;
    }
  }

  /** One declared save-time or publication result (the spec's {@code AuthoringFinding}). */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class AuthoringFinding {
    @JsonProperty("code")
    private final String code;

    @JsonProperty("severity")
    private final String severity;

    @JsonProperty("policy_id")
    private final String policyId;

    @JsonProperty("summary")
    private final String summary;

    @JsonProperty("detail")
    private final String detail;

    @JsonCreator
    public AuthoringFinding(
        @JsonProperty("code") String code,
        @JsonProperty("severity") String severity,
        @JsonProperty("policy_id") String policyId,
        @JsonProperty("summary") String summary,
        @JsonProperty("detail") String detail) {
      this.code = code;
      this.severity = severity;
      this.policyId = policyId;
      this.summary = summary;
      this.detail = detail;
    }

    public String getCode() {
      return code;
    }

    /** Returns reject or warn. */
    public String getSeverity() {
      return severity;
    }

    public String getPolicyId() {
      return policyId;
    }

    /** Returns the declared, code-level sentence. */
    public String getSummary() {
      return summary;
    }

    /** Returns what was wrong, naming the offending value. */
    public String getDetail() {
      return detail;
    }

    @Override
    public String toString() {
      return code + " " + severity + (policyId == null ? "" : " " + policyId);
    }
  }

  /**
   * A candidate document and its fixtures (the spec's {@code TypedAuthoringDocumentRequest}).
   *
   * <p>The document is the authoring model itself, kept as a JSON object rather than mirrored in
   * Java types, so a field the policy vocabulary gains is authorable without an SDK release. A null
   * fixtures list sends no fixtures member; an empty one sends {@code []}, which the platform
   * answers differently.
   */
  public static final class TypedAuthoringDocumentRequest {
    @JsonProperty("document")
    private final Map<String, Object> document;

    @JsonProperty("fixtures")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final List<Map<String, Object>> fixtures;

    public TypedAuthoringDocumentRequest(
        Map<String, Object> document, List<Map<String, Object>> fixtures) {
      this.document = document;
      this.fixtures = fixtures;
    }

    public Map<String, Object> getDocument() {
      return document;
    }

    public List<Map<String, Object>> getFixtures() {
      return fixtures;
    }
  }

  /** What this deployment may author. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class TypedAuthoringEdition {
    @JsonProperty("success")
    private final boolean success;

    @JsonProperty("catalog")
    private final String catalog;

    @JsonProperty("root")
    private final String root;

    @JsonProperty("max_documents")
    private final Integer maxDocuments;

    @JsonProperty("constructs")
    private final EditionConstructReport constructs;

    @JsonProperty("persistence")
    private final String persistence;

    @JsonProperty("signing_key_custody")
    private final String signingKeyCustody;

    @JsonCreator
    public TypedAuthoringEdition(
        @JsonProperty("success") boolean success,
        @JsonProperty("catalog") String catalog,
        @JsonProperty("root") String root,
        @JsonProperty("max_documents") Integer maxDocuments,
        @JsonProperty("constructs") EditionConstructReport constructs,
        @JsonProperty("persistence") String persistence,
        @JsonProperty("signing_key_custody") String signingKeyCustody) {
      this.success = success;
      this.catalog = catalog;
      this.root = root;
      this.maxDocuments = maxDocuments;
      this.constructs = constructs;
      this.persistence = persistence;
      this.signingKeyCustody = signingKeyCustody;
    }

    public boolean isSuccess() {
      return success;
    }

    /** Returns the configured authoring vocabulary. */
    public String getCatalog() {
      return catalog;
    }

    /** Returns the one authority root this surface publishes under. */
    public String getRoot() {
      return root;
    }

    /** Returns the customer-authored documents admitted per organization; -1 is unlimited. */
    public Integer getMaxDocuments() {
      return maxDocuments;
    }

    public EditionConstructReport getConstructs() {
      return constructs;
    }

    /** Returns process, database or unavailable. */
    public String getPersistence() {
      return persistence;
    }

    public String getSigningKeyCustody() {
      return signingKeyCustody;
    }
  }

  /** Every finding for a candidate document. Success is false when any is a rejection. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class TypedPolicyValidation {
    @JsonProperty("success")
    private final boolean success;

    @JsonProperty("findings")
    private final List<AuthoringFinding> findings;

    @JsonCreator
    public TypedPolicyValidation(
        @JsonProperty("success") boolean success,
        @JsonProperty("findings") List<AuthoringFinding> findings) {
      this.success = success;
      this.findings = list(findings);
    }

    public boolean isSuccess() {
      return success;
    }

    public List<AuthoringFinding> getFindings() {
      return findings;
    }
  }

  /** A published artifact. Activation names the digest, never the version. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class TypedPolicyPublication {
    @JsonProperty("success")
    private final boolean success;

    @JsonProperty("digest")
    private final String digest;

    @JsonProperty("version")
    private final Integer version;

    @JsonProperty("findings")
    private final List<AuthoringFinding> findings;

    @JsonCreator
    public TypedPolicyPublication(
        @JsonProperty("success") boolean success,
        @JsonProperty("digest") String digest,
        @JsonProperty("version") Integer version,
        @JsonProperty("findings") List<AuthoringFinding> findings) {
      this.success = success;
      this.digest = digest;
      this.version = version;
      this.findings = list(findings);
    }

    public boolean isSuccess() {
      return success;
    }

    public String getDigest() {
      return digest;
    }

    public Integer getVersion() {
      return version;
    }

    public List<AuthoringFinding> getFindings() {
      return findings;
    }
  }

  /** The audited activation record. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class TypedPolicyActivation {
    @JsonProperty("success")
    private final boolean success;

    @JsonProperty("activation")
    private final Map<String, Object> activation;

    @JsonCreator
    public TypedPolicyActivation(
        @JsonProperty("success") boolean success,
        @JsonProperty("activation") Map<String, Object> activation) {
      this.success = success;
      this.activation = map(activation);
    }

    public boolean isSuccess() {
      return success;
    }

    public Map<String, Object> getActivation() {
      return activation;
    }
  }

  /**
   * The document in force. The source is the exact byte sequence that was signed, decoded as UTF-8,
   * so a caller can verify it; the document is the same bytes parsed.
   */
  public static final class ActiveTypedPolicy {
    private final String source;
    private final Map<String, Object> document;

    public ActiveTypedPolicy(String source, Map<String, Object> document) {
      this.source = source;
      this.document = map(document);
    }

    public String getSource() {
      return source;
    }

    public Map<String, Object> getDocument() {
      return document;
    }
  }

  /** One shipped control, with what happens when it cannot be evaluated. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class TypedPolicySystemControl {
    @JsonProperty("id")
    private final String id;

    @JsonProperty("authority")
    private final String authority;

    @JsonProperty("assurance")
    private final String assurance;

    @JsonProperty("mandatory")
    private final Boolean mandatory;

    @JsonProperty("description")
    private final String description;

    @JsonProperty("obligations")
    private final List<Map<String, Object>> obligations;

    @JsonCreator
    public TypedPolicySystemControl(
        @JsonProperty("id") String id,
        @JsonProperty("authority") String authority,
        @JsonProperty("assurance") String assurance,
        @JsonProperty("mandatory") Boolean mandatory,
        @JsonProperty("description") String description,
        @JsonProperty("obligations") List<Map<String, Object>> obligations) {
      this.id = id;
      this.authority = authority;
      this.assurance = assurance;
      this.mandatory = mandatory;
      this.description = description;
      this.obligations = list(obligations);
    }

    public String getId() {
      return id;
    }

    public String getAuthority() {
      return authority;
    }

    /** Returns enforcement, gating_risk or advisory. */
    public String getAssurance() {
      return assurance;
    }

    public Boolean getMandatory() {
      return mandatory;
    }

    public String getDescription() {
      return description;
    }

    public List<Map<String, Object>> getObligations() {
      return obligations;
    }
  }

  /** The platform's own controls: the system root activated beneath every organization. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class TypedPolicySystemCorpus {
    @JsonProperty("root")
    private final String root;

    @JsonProperty("version")
    private final Integer version;

    @JsonProperty("digest")
    private final String digest;

    @JsonProperty("authority")
    private final String authority;

    @JsonProperty("controls")
    private final List<TypedPolicySystemControl> controls;

    @JsonProperty("assurance_counts")
    private final Map<String, Integer> assuranceCounts;

    @JsonProperty("document")
    private final Map<String, Object> document;

    @JsonCreator
    public TypedPolicySystemCorpus(
        @JsonProperty("root") String root,
        @JsonProperty("version") Integer version,
        @JsonProperty("digest") String digest,
        @JsonProperty("authority") String authority,
        @JsonProperty("controls") List<TypedPolicySystemControl> controls,
        @JsonProperty("assurance_counts") Map<String, Integer> assuranceCounts,
        @JsonProperty("document") Map<String, Object> document) {
      this.root = root;
      this.version = version;
      this.digest = digest;
      this.authority = authority;
      this.controls = list(controls);
      this.assuranceCounts = map(assuranceCounts);
      this.document = map(document);
    }

    /** Returns an empty corpus, for an answer that carries none. */
    public static TypedPolicySystemCorpus empty() {
      return new TypedPolicySystemCorpus(null, null, null, null, null, null, null);
    }

    public String getRoot() {
      return root;
    }

    public Integer getVersion() {
      return version;
    }

    /** Returns the digest an enforcing engine anchors to. */
    public String getDigest() {
      return digest;
    }

    public String getAuthority() {
      return authority;
    }

    public List<TypedPolicySystemControl> getControls() {
      return controls;
    }

    public Map<String, Integer> getAssuranceCounts() {
      return assuranceCounts;
    }

    public Map<String, Object> getDocument() {
      return document;
    }
  }
}
