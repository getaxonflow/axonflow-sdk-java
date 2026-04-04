package com.getaxonflow.sdk.masfeat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * MAS FEAT Compliance Types for Singapore Regulatory Compliance.
 *
 * <p>This class contains all types for the MAS FEAT (Monetary Authority of Singapore -
 * Fairness, Ethics, Accountability, Transparency) compliance module.
 *
 * <p><b>Enterprise Feature:</b> Requires AxonFlow Enterprise license.
 */
public final class MASFEATTypes {

    private MASFEATTypes() {
        // Utility class
    }

    // =========================================================================
    // Enums
    // =========================================================================

    /** Materiality classification based on 3D risk rating. */
    public enum MaterialityClassification {
        HIGH("high"),
        MEDIUM("medium"),
        LOW("low");

        private final String value;

        MaterialityClassification(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static MaterialityClassification fromValue(String value) {
            for (MaterialityClassification e : values()) {
                if (e.value.equals(value)) {
                    return e;
                }
            }
            throw new IllegalArgumentException("Unknown materiality: " + value);
        }
    }

    /** AI system lifecycle status. */
    public enum SystemStatus {
        DRAFT("draft"),
        ACTIVE("active"),
        SUSPENDED("suspended"),
        RETIRED("retired");

        private final String value;

        SystemStatus(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static SystemStatus fromValue(String value) {
            for (SystemStatus e : values()) {
                if (e.value.equals(value)) {
                    return e;
                }
            }
            throw new IllegalArgumentException("Unknown status: " + value);
        }
    }

    /** FEAT assessment lifecycle status. */
    public enum FEATAssessmentStatus {
        PENDING("pending"),
        IN_PROGRESS("in_progress"),
        COMPLETED("completed"),
        APPROVED("approved"),
        REJECTED("rejected");

        private final String value;

        FEATAssessmentStatus(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static FEATAssessmentStatus fromValue(String value) {
            for (FEATAssessmentStatus e : values()) {
                if (e.value.equals(value)) {
                    return e;
                }
            }
            throw new IllegalArgumentException("Unknown assessment status: " + value);
        }
    }

    /** Kill switch status. */
    public enum KillSwitchStatus {
        ENABLED("enabled"),
        DISABLED("disabled"),
        TRIGGERED("triggered");

        private final String value;

        KillSwitchStatus(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static KillSwitchStatus fromValue(String value) {
            for (KillSwitchStatus e : values()) {
                if (e.value.equals(value)) {
                    return e;
                }
            }
            throw new IllegalArgumentException("Unknown kill switch status: " + value);
        }
    }

    /** AI system use case categories. */
    public enum AISystemUseCase {
        CREDIT_SCORING("credit_scoring"),
        ROBO_ADVISORY("robo_advisory"),
        INSURANCE_UNDERWRITING("insurance_underwriting"),
        TRADING_ALGORITHM("trading_algorithm"),
        AML_CFT("aml_cft"),
        CUSTOMER_SERVICE("customer_service"),
        FRAUD_DETECTION("fraud_detection"),
        OTHER("other");

        private final String value;

        AISystemUseCase(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static AISystemUseCase fromValue(String value) {
            for (AISystemUseCase e : values()) {
                if (e.value.equals(value)) {
                    return e;
                }
            }
            throw new IllegalArgumentException("Unknown use case: " + value);
        }
    }

    /** FEAT framework pillars. */
    public enum FEATPillar {
        FAIRNESS("fairness"),
        ETHICS("ethics"),
        ACCOUNTABILITY("accountability"),
        TRANSPARENCY("transparency");

        private final String value;

        FEATPillar(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static FEATPillar fromValue(String value) {
            for (FEATPillar e : values()) {
                if (e.value.equals(value)) {
                    return e;
                }
            }
            throw new IllegalArgumentException("Unknown pillar: " + value);
        }
    }

    /** FEAT assessment finding severity. */
    public enum FindingSeverity {
        CRITICAL("critical"),
        MAJOR("major"),
        MINOR("minor"),
        OBSERVATION("observation");

        private final String value;

        FindingSeverity(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static FindingSeverity fromValue(String value) {
            for (FindingSeverity e : values()) {
                if (e.value.equals(value)) {
                    return e;
                }
            }
            throw new IllegalArgumentException("Unknown finding severity: " + value);
        }
    }

    /** FEAT assessment finding status. */
    public enum FindingStatus {
        OPEN("open"),
        RESOLVED("resolved"),
        ACCEPTED("accepted");

        private final String value;

        FindingStatus(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static FindingStatus fromValue(String value) {
            for (FindingStatus e : values()) {
                if (e.value.equals(value)) {
                    return e;
                }
            }
            throw new IllegalArgumentException("Unknown finding status: " + value);
        }
    }

    // =========================================================================
    // Finding Type
    // =========================================================================

    /** A FEAT assessment finding. */
    public static class Finding {
        private String id;
        private FEATPillar pillar;
        private FindingSeverity severity;
        private String category;
        private String description;
        private FindingStatus status;
        private String remediation;
        private Instant dueDate;

        public Finding() {}

        private Finding(Builder builder) {
            this.id = builder.id;
            this.pillar = builder.pillar;
            this.severity = builder.severity;
            this.category = builder.category;
            this.description = builder.description;
            this.status = builder.status;
            this.remediation = builder.remediation;
            this.dueDate = builder.dueDate;
        }

        public static Builder builder() {
            return new Builder();
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public FEATPillar getPillar() { return pillar; }
        public void setPillar(FEATPillar pillar) { this.pillar = pillar; }
        public FindingSeverity getSeverity() { return severity; }
        public void setSeverity(FindingSeverity severity) { this.severity = severity; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public FindingStatus getStatus() { return status; }
        public void setStatus(FindingStatus status) { this.status = status; }
        public String getRemediation() { return remediation; }
        public void setRemediation(String remediation) { this.remediation = remediation; }
        public Instant getDueDate() { return dueDate; }
        public void setDueDate(Instant dueDate) { this.dueDate = dueDate; }

        public static class Builder {
            private String id;
            private FEATPillar pillar;
            private FindingSeverity severity;
            private String category;
            private String description;
            private FindingStatus status;
            private String remediation;
            private Instant dueDate;

            public Builder id(String id) { this.id = id; return this; }
            public Builder pillar(FEATPillar pillar) { this.pillar = pillar; return this; }
            public Builder severity(FindingSeverity severity) { this.severity = severity; return this; }
            public Builder category(String category) { this.category = category; return this; }
            public Builder description(String description) { this.description = description; return this; }
            public Builder status(FindingStatus status) { this.status = status; return this; }
            public Builder remediation(String remediation) { this.remediation = remediation; return this; }
            public Builder dueDate(Instant dueDate) { this.dueDate = dueDate; return this; }

            public Finding build() {
                return new Finding(this);
            }
        }
    }

    // =========================================================================
    // AI System Registry Types
    // =========================================================================

    /** Request to register an AI system. */
    public static class RegisterSystemRequest {
        private final String systemId;
        private final String systemName;
        private final AISystemUseCase useCase;
        private final String ownerTeam;
        private final int customerImpact;
        private final int modelComplexity;
        private final int humanReliance;
        private String description;
        private String technicalOwner;
        private String businessOwner;
        private Map<String, Object> metadata;

        private RegisterSystemRequest(Builder builder) {
            this.systemId = builder.systemId;
            this.systemName = builder.systemName;
            this.useCase = builder.useCase;
            this.ownerTeam = builder.ownerTeam;
            this.customerImpact = builder.customerImpact;
            this.modelComplexity = builder.modelComplexity;
            this.humanReliance = builder.humanReliance;
            this.description = builder.description;
            this.technicalOwner = builder.technicalOwner;
            this.businessOwner = builder.businessOwner;
            this.metadata = builder.metadata;
        }

        public static Builder builder() {
            return new Builder();
        }

        public String getSystemId() { return systemId; }
        public String getSystemName() { return systemName; }
        public AISystemUseCase getUseCase() { return useCase; }
        public String getOwnerTeam() { return ownerTeam; }
        public int getCustomerImpact() { return customerImpact; }
        public int getModelComplexity() { return modelComplexity; }
        public int getHumanReliance() { return humanReliance; }
        public String getDescription() { return description; }
        public String getTechnicalOwner() { return technicalOwner; }
        public String getBusinessOwner() { return businessOwner; }
        public Map<String, Object> getMetadata() { return metadata; }

        public static class Builder {
            private String systemId;
            private String systemName;
            private AISystemUseCase useCase;
            private String ownerTeam;
            private int customerImpact;
            private int modelComplexity;
            private int humanReliance;
            private String description;
            private String technicalOwner;
            private String businessOwner;
            private Map<String, Object> metadata;

            public Builder systemId(String systemId) { this.systemId = systemId; return this; }
            public Builder systemName(String systemName) { this.systemName = systemName; return this; }
            public Builder useCase(AISystemUseCase useCase) { this.useCase = useCase; return this; }
            public Builder ownerTeam(String ownerTeam) { this.ownerTeam = ownerTeam; return this; }
            public Builder customerImpact(int customerImpact) { this.customerImpact = customerImpact; return this; }
            public Builder modelComplexity(int modelComplexity) { this.modelComplexity = modelComplexity; return this; }
            public Builder humanReliance(int humanReliance) { this.humanReliance = humanReliance; return this; }
            public Builder description(String description) { this.description = description; return this; }
            public Builder technicalOwner(String technicalOwner) { this.technicalOwner = technicalOwner; return this; }
            public Builder businessOwner(String businessOwner) { this.businessOwner = businessOwner; return this; }
            public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }

            public RegisterSystemRequest build() {
                return new RegisterSystemRequest(this);
            }
        }
    }

    /** AI system registry entry. */
    public static class AISystemRegistry {
        private String id;
        private String orgId;
        private String systemId;
        private String systemName;
        private AISystemUseCase useCase;
        private String ownerTeam;
        private int customerImpact;
        private int modelComplexity;
        private int humanReliance;
        @com.fasterxml.jackson.annotation.JsonProperty("materiality_classification")
        private MaterialityClassification materialityClassification;
        private SystemStatus status;
        private Instant createdAt;
        private Instant updatedAt;
        private String description;
        private String technicalOwner;
        private String businessOwner;
        private Map<String, Object> metadata;
        private String createdBy;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getOrgId() { return orgId; }
        public void setOrgId(String orgId) { this.orgId = orgId; }
        public String getSystemId() { return systemId; }
        public void setSystemId(String systemId) { this.systemId = systemId; }
        public String getSystemName() { return systemName; }
        public void setSystemName(String systemName) { this.systemName = systemName; }
        public AISystemUseCase getUseCase() { return useCase; }
        public void setUseCase(AISystemUseCase useCase) { this.useCase = useCase; }
        public String getOwnerTeam() { return ownerTeam; }
        public void setOwnerTeam(String ownerTeam) { this.ownerTeam = ownerTeam; }
        public int getCustomerImpact() { return customerImpact; }
        public void setCustomerImpact(int customerImpact) { this.customerImpact = customerImpact; }
        public int getModelComplexity() { return modelComplexity; }
        public void setModelComplexity(int modelComplexity) { this.modelComplexity = modelComplexity; }
        public int getHumanReliance() { return humanReliance; }
        public void setHumanReliance(int humanReliance) { this.humanReliance = humanReliance; }
        public MaterialityClassification getMaterialityClassification() { return materialityClassification; }
        public void setMaterialityClassification(MaterialityClassification materialityClassification) { this.materialityClassification = materialityClassification; }
        public SystemStatus getStatus() { return status; }
        public void setStatus(SystemStatus status) { this.status = status; }
        public Instant getCreatedAt() { return createdAt; }
        public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
        public Instant getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getTechnicalOwner() { return technicalOwner; }
        public void setTechnicalOwner(String technicalOwner) { this.technicalOwner = technicalOwner; }
        public String getBusinessOwner() { return businessOwner; }
        public void setBusinessOwner(String businessOwner) { this.businessOwner = businessOwner; }
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
        public String getCreatedBy() { return createdBy; }
        public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    }

    /** Registry summary statistics. */
    public static class RegistrySummary {
        private int totalSystems;
        private int activeSystems;
        private int highMaterialityCount;
        private int mediumMaterialityCount;
        private int lowMaterialityCount;
        private Map<String, Integer> byUseCase;
        private Map<String, Integer> byStatus;

        public int getTotalSystems() { return totalSystems; }
        public void setTotalSystems(int totalSystems) { this.totalSystems = totalSystems; }
        public int getActiveSystems() { return activeSystems; }
        public void setActiveSystems(int activeSystems) { this.activeSystems = activeSystems; }
        public int getHighMaterialityCount() { return highMaterialityCount; }
        public void setHighMaterialityCount(int highMaterialityCount) { this.highMaterialityCount = highMaterialityCount; }
        public int getMediumMaterialityCount() { return mediumMaterialityCount; }
        public void setMediumMaterialityCount(int mediumMaterialityCount) { this.mediumMaterialityCount = mediumMaterialityCount; }
        public int getLowMaterialityCount() { return lowMaterialityCount; }
        public void setLowMaterialityCount(int lowMaterialityCount) { this.lowMaterialityCount = lowMaterialityCount; }
        public Map<String, Integer> getByUseCase() { return byUseCase; }
        public void setByUseCase(Map<String, Integer> byUseCase) { this.byUseCase = byUseCase; }
        public Map<String, Integer> getByStatus() { return byStatus; }
        public void setByStatus(Map<String, Integer> byStatus) { this.byStatus = byStatus; }
    }

    // =========================================================================
    // FEAT Assessment Types
    // =========================================================================

    /** Request to create a FEAT assessment. */
    public static class CreateAssessmentRequest {
        private final String systemId;
        private String assessmentType = "initial";
        private List<String> assessors;

        private CreateAssessmentRequest(Builder builder) {
            this.systemId = builder.systemId;
            this.assessmentType = builder.assessmentType;
            this.assessors = builder.assessors;
        }

        public static Builder builder() {
            return new Builder();
        }

        public String getSystemId() { return systemId; }
        public String getAssessmentType() { return assessmentType; }
        public List<String> getAssessors() { return assessors; }

        public static class Builder {
            private String systemId;
            private String assessmentType = "initial";
            private List<String> assessors;

            public Builder systemId(String systemId) { this.systemId = systemId; return this; }
            public Builder assessmentType(String assessmentType) { this.assessmentType = assessmentType; return this; }
            public Builder assessors(List<String> assessors) { this.assessors = assessors; return this; }

            public CreateAssessmentRequest build() {
                return new CreateAssessmentRequest(this);
            }
        }
    }

    /** Request to update a FEAT assessment. */
    public static class UpdateAssessmentRequest {
        private Integer fairnessScore;
        private Integer ethicsScore;
        private Integer accountabilityScore;
        private Integer transparencyScore;
        private Map<String, Object> fairnessDetails;
        private Map<String, Object> ethicsDetails;
        private Map<String, Object> accountabilityDetails;
        private Map<String, Object> transparencyDetails;
        private List<Finding> findings;
        private List<String> recommendations;
        private List<String> assessors;

        private UpdateAssessmentRequest(Builder builder) {
            this.fairnessScore = builder.fairnessScore;
            this.ethicsScore = builder.ethicsScore;
            this.accountabilityScore = builder.accountabilityScore;
            this.transparencyScore = builder.transparencyScore;
            this.fairnessDetails = builder.fairnessDetails;
            this.ethicsDetails = builder.ethicsDetails;
            this.accountabilityDetails = builder.accountabilityDetails;
            this.transparencyDetails = builder.transparencyDetails;
            this.findings = builder.findings;
            this.recommendations = builder.recommendations;
            this.assessors = builder.assessors;
        }

        public static Builder builder() {
            return new Builder();
        }

        public Integer getFairnessScore() { return fairnessScore; }
        public Integer getEthicsScore() { return ethicsScore; }
        public Integer getAccountabilityScore() { return accountabilityScore; }
        public Integer getTransparencyScore() { return transparencyScore; }
        public Map<String, Object> getFairnessDetails() { return fairnessDetails; }
        public Map<String, Object> getEthicsDetails() { return ethicsDetails; }
        public Map<String, Object> getAccountabilityDetails() { return accountabilityDetails; }
        public Map<String, Object> getTransparencyDetails() { return transparencyDetails; }
        public List<Finding> getFindings() { return findings; }
        public List<String> getRecommendations() { return recommendations; }
        public List<String> getAssessors() { return assessors; }

        public static class Builder {
            private Integer fairnessScore;
            private Integer ethicsScore;
            private Integer accountabilityScore;
            private Integer transparencyScore;
            private Map<String, Object> fairnessDetails;
            private Map<String, Object> ethicsDetails;
            private Map<String, Object> accountabilityDetails;
            private Map<String, Object> transparencyDetails;
            private List<Finding> findings;
            private List<String> recommendations;
            private List<String> assessors;

            public Builder fairnessScore(int score) { this.fairnessScore = score; return this; }
            public Builder ethicsScore(int score) { this.ethicsScore = score; return this; }
            public Builder accountabilityScore(int score) { this.accountabilityScore = score; return this; }
            public Builder transparencyScore(int score) { this.transparencyScore = score; return this; }
            public Builder fairnessDetails(Map<String, Object> details) { this.fairnessDetails = details; return this; }
            public Builder ethicsDetails(Map<String, Object> details) { this.ethicsDetails = details; return this; }
            public Builder accountabilityDetails(Map<String, Object> details) { this.accountabilityDetails = details; return this; }
            public Builder transparencyDetails(Map<String, Object> details) { this.transparencyDetails = details; return this; }
            public Builder findings(List<Finding> findings) { this.findings = findings; return this; }
            public Builder recommendations(List<String> recommendations) { this.recommendations = recommendations; return this; }
            public Builder assessors(List<String> assessors) { this.assessors = assessors; return this; }

            public UpdateAssessmentRequest build() {
                return new UpdateAssessmentRequest(this);
            }
        }
    }

    /** FEAT assessment record. */
    public static class FEATAssessment {
        private String id;
        private String orgId;
        private String systemId;
        private String assessmentType;
        private FEATAssessmentStatus status;
        private Instant assessmentDate;
        private Instant validUntil;
        private Integer fairnessScore;
        private Integer ethicsScore;
        private Integer accountabilityScore;
        private Integer transparencyScore;
        private Integer overallScore;
        private Map<String, Object> fairnessDetails;
        private Map<String, Object> ethicsDetails;
        private Map<String, Object> accountabilityDetails;
        private Map<String, Object> transparencyDetails;
        private List<Finding> findings;
        private List<String> recommendations;
        private List<String> assessors;
        private String approvedBy;
        private Instant approvedAt;
        private Instant createdAt;
        private Instant updatedAt;
        private String createdBy;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getOrgId() { return orgId; }
        public void setOrgId(String orgId) { this.orgId = orgId; }
        public String getSystemId() { return systemId; }
        public void setSystemId(String systemId) { this.systemId = systemId; }
        public String getAssessmentType() { return assessmentType; }
        public void setAssessmentType(String assessmentType) { this.assessmentType = assessmentType; }
        public FEATAssessmentStatus getStatus() { return status; }
        public void setStatus(FEATAssessmentStatus status) { this.status = status; }
        public Instant getAssessmentDate() { return assessmentDate; }
        public void setAssessmentDate(Instant assessmentDate) { this.assessmentDate = assessmentDate; }
        public Instant getValidUntil() { return validUntil; }
        public void setValidUntil(Instant validUntil) { this.validUntil = validUntil; }
        public Integer getFairnessScore() { return fairnessScore; }
        public void setFairnessScore(Integer fairnessScore) { this.fairnessScore = fairnessScore; }
        public Integer getEthicsScore() { return ethicsScore; }
        public void setEthicsScore(Integer ethicsScore) { this.ethicsScore = ethicsScore; }
        public Integer getAccountabilityScore() { return accountabilityScore; }
        public void setAccountabilityScore(Integer accountabilityScore) { this.accountabilityScore = accountabilityScore; }
        public Integer getTransparencyScore() { return transparencyScore; }
        public void setTransparencyScore(Integer transparencyScore) { this.transparencyScore = transparencyScore; }
        public Integer getOverallScore() { return overallScore; }
        public void setOverallScore(Integer overallScore) { this.overallScore = overallScore; }
        public Map<String, Object> getFairnessDetails() { return fairnessDetails; }
        public void setFairnessDetails(Map<String, Object> fairnessDetails) { this.fairnessDetails = fairnessDetails; }
        public Map<String, Object> getEthicsDetails() { return ethicsDetails; }
        public void setEthicsDetails(Map<String, Object> ethicsDetails) { this.ethicsDetails = ethicsDetails; }
        public Map<String, Object> getAccountabilityDetails() { return accountabilityDetails; }
        public void setAccountabilityDetails(Map<String, Object> accountabilityDetails) { this.accountabilityDetails = accountabilityDetails; }
        public Map<String, Object> getTransparencyDetails() { return transparencyDetails; }
        public void setTransparencyDetails(Map<String, Object> transparencyDetails) { this.transparencyDetails = transparencyDetails; }
        public List<Finding> getFindings() { return findings; }
        public void setFindings(List<Finding> findings) { this.findings = findings; }
        public List<String> getRecommendations() { return recommendations; }
        public void setRecommendations(List<String> recommendations) { this.recommendations = recommendations; }
        public List<String> getAssessors() { return assessors; }
        public void setAssessors(List<String> assessors) { this.assessors = assessors; }
        public String getApprovedBy() { return approvedBy; }
        public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
        public Instant getApprovedAt() { return approvedAt; }
        public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }
        public Instant getCreatedAt() { return createdAt; }
        public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
        public Instant getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
        public String getCreatedBy() { return createdBy; }
        public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    }

    /** Request to approve an assessment. */
    public static class ApproveAssessmentRequest {
        private final String approvedBy;
        private String comments;

        private ApproveAssessmentRequest(Builder builder) {
            this.approvedBy = builder.approvedBy;
            this.comments = builder.comments;
        }

        public static Builder builder() {
            return new Builder();
        }

        public String getApprovedBy() { return approvedBy; }
        public String getComments() { return comments; }

        public static class Builder {
            private String approvedBy;
            private String comments;

            public Builder approvedBy(String approvedBy) { this.approvedBy = approvedBy; return this; }
            public Builder comments(String comments) { this.comments = comments; return this; }

            public ApproveAssessmentRequest build() {
                return new ApproveAssessmentRequest(this);
            }
        }
    }

    /** Request to reject an assessment. */
    public static class RejectAssessmentRequest {
        private final String rejectedBy;
        private final String reason;

        private RejectAssessmentRequest(Builder builder) {
            this.rejectedBy = builder.rejectedBy;
            this.reason = builder.reason;
        }

        public static Builder builder() {
            return new Builder();
        }

        public String getRejectedBy() { return rejectedBy; }
        public String getReason() { return reason; }

        public static class Builder {
            private String rejectedBy;
            private String reason;

            public Builder rejectedBy(String rejectedBy) { this.rejectedBy = rejectedBy; return this; }
            public Builder reason(String reason) { this.reason = reason; return this; }

            public RejectAssessmentRequest build() {
                return new RejectAssessmentRequest(this);
            }
        }
    }

    // =========================================================================
    // Kill Switch Types
    // =========================================================================

    /** Kill switch configuration. */
    public static class KillSwitch {
        private String id;
        private String orgId;
        private String systemId;
        private KillSwitchStatus status;
        private boolean autoTriggerEnabled;
        private Double accuracyThreshold;
        private Double biasThreshold;
        private Double errorRateThreshold;
        private Instant triggeredAt;
        private String triggeredBy;
        private String triggeredReason;
        private Instant restoredAt;
        private String restoredBy;
        private Instant createdAt;
        private Instant updatedAt;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getOrgId() { return orgId; }
        public void setOrgId(String orgId) { this.orgId = orgId; }
        public String getSystemId() { return systemId; }
        public void setSystemId(String systemId) { this.systemId = systemId; }
        public KillSwitchStatus getStatus() { return status; }
        public void setStatus(KillSwitchStatus status) { this.status = status; }
        public boolean isAutoTriggerEnabled() { return autoTriggerEnabled; }
        public void setAutoTriggerEnabled(boolean autoTriggerEnabled) { this.autoTriggerEnabled = autoTriggerEnabled; }
        public Double getAccuracyThreshold() { return accuracyThreshold; }
        public void setAccuracyThreshold(Double accuracyThreshold) { this.accuracyThreshold = accuracyThreshold; }
        public Double getBiasThreshold() { return biasThreshold; }
        public void setBiasThreshold(Double biasThreshold) { this.biasThreshold = biasThreshold; }
        public Double getErrorRateThreshold() { return errorRateThreshold; }
        public void setErrorRateThreshold(Double errorRateThreshold) { this.errorRateThreshold = errorRateThreshold; }
        public Instant getTriggeredAt() { return triggeredAt; }
        public void setTriggeredAt(Instant triggeredAt) { this.triggeredAt = triggeredAt; }
        public String getTriggeredBy() { return triggeredBy; }
        public void setTriggeredBy(String triggeredBy) { this.triggeredBy = triggeredBy; }
        public String getTriggeredReason() { return triggeredReason; }
        public void setTriggeredReason(String triggeredReason) { this.triggeredReason = triggeredReason; }
        public Instant getRestoredAt() { return restoredAt; }
        public void setRestoredAt(Instant restoredAt) { this.restoredAt = restoredAt; }
        public String getRestoredBy() { return restoredBy; }
        public void setRestoredBy(String restoredBy) { this.restoredBy = restoredBy; }
        public Instant getCreatedAt() { return createdAt; }
        public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
        public Instant getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    }

    /** Request to configure a kill switch. */
    public static class ConfigureKillSwitchRequest {
        private Double accuracyThreshold;
        private Double biasThreshold;
        private Double errorRateThreshold;
        private Boolean autoTriggerEnabled;

        private ConfigureKillSwitchRequest(Builder builder) {
            this.accuracyThreshold = builder.accuracyThreshold;
            this.biasThreshold = builder.biasThreshold;
            this.errorRateThreshold = builder.errorRateThreshold;
            this.autoTriggerEnabled = builder.autoTriggerEnabled;
        }

        public static Builder builder() {
            return new Builder();
        }

        public Double getAccuracyThreshold() { return accuracyThreshold; }
        public Double getBiasThreshold() { return biasThreshold; }
        public Double getErrorRateThreshold() { return errorRateThreshold; }
        public Boolean getAutoTriggerEnabled() { return autoTriggerEnabled; }

        public static class Builder {
            private Double accuracyThreshold;
            private Double biasThreshold;
            private Double errorRateThreshold;
            private Boolean autoTriggerEnabled;

            public Builder accuracyThreshold(double threshold) { this.accuracyThreshold = threshold; return this; }
            public Builder biasThreshold(double threshold) { this.biasThreshold = threshold; return this; }
            public Builder errorRateThreshold(double threshold) { this.errorRateThreshold = threshold; return this; }
            public Builder autoTriggerEnabled(boolean enabled) { this.autoTriggerEnabled = enabled; return this; }

            public ConfigureKillSwitchRequest build() {
                return new ConfigureKillSwitchRequest(this);
            }
        }
    }

    /** Request to check kill switch metrics. */
    public static class CheckKillSwitchRequest {
        private final double accuracy;
        private Double biasScore;
        private Double errorRate;

        private CheckKillSwitchRequest(Builder builder) {
            this.accuracy = builder.accuracy;
            this.biasScore = builder.biasScore;
            this.errorRate = builder.errorRate;
        }

        public static Builder builder() {
            return new Builder();
        }

        public double getAccuracy() { return accuracy; }
        public Double getBiasScore() { return biasScore; }
        public Double getErrorRate() { return errorRate; }

        public static class Builder {
            private double accuracy;
            private Double biasScore;
            private Double errorRate;

            public Builder accuracy(double accuracy) { this.accuracy = accuracy; return this; }
            public Builder biasScore(double biasScore) { this.biasScore = biasScore; return this; }
            public Builder errorRate(double errorRate) { this.errorRate = errorRate; return this; }

            public CheckKillSwitchRequest build() {
                return new CheckKillSwitchRequest(this);
            }
        }
    }

    /** Request to trigger a kill switch. */
    public static class TriggerKillSwitchRequest {
        private final String reason;
        private String triggeredBy;

        private TriggerKillSwitchRequest(Builder builder) {
            this.reason = builder.reason;
            this.triggeredBy = builder.triggeredBy;
        }

        public static Builder builder() {
            return new Builder();
        }

        public String getReason() { return reason; }
        public String getTriggeredBy() { return triggeredBy; }

        public static class Builder {
            private String reason;
            private String triggeredBy;

            public Builder reason(String reason) { this.reason = reason; return this; }
            public Builder triggeredBy(String triggeredBy) { this.triggeredBy = triggeredBy; return this; }

            public TriggerKillSwitchRequest build() {
                return new TriggerKillSwitchRequest(this);
            }
        }
    }

    /** Request to restore a kill switch. */
    public static class RestoreKillSwitchRequest {
        private final String reason;
        private String restoredBy;

        private RestoreKillSwitchRequest(Builder builder) {
            this.reason = builder.reason;
            this.restoredBy = builder.restoredBy;
        }

        public static Builder builder() {
            return new Builder();
        }

        public String getReason() { return reason; }
        public String getRestoredBy() { return restoredBy; }

        public static class Builder {
            private String reason;
            private String restoredBy;

            public Builder reason(String reason) { this.reason = reason; return this; }
            public Builder restoredBy(String restoredBy) { this.restoredBy = restoredBy; return this; }

            public RestoreKillSwitchRequest build() {
                return new RestoreKillSwitchRequest(this);
            }
        }
    }

    /** Kill switch event record. */
    public static class KillSwitchEvent {
        private String id;
        private String killSwitchId;
        private String eventType;
        private Map<String, Object> eventData;
        private String createdBy;
        private Instant createdAt;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getKillSwitchId() { return killSwitchId; }
        public void setKillSwitchId(String killSwitchId) { this.killSwitchId = killSwitchId; }
        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }
        public Map<String, Object> getEventData() { return eventData; }
        public void setEventData(Map<String, Object> eventData) { this.eventData = eventData; }
        public String getCreatedBy() { return createdBy; }
        public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
        public Instant getCreatedAt() { return createdAt; }
        public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    }
}
