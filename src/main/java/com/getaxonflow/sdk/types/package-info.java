/*
 * Copyright 2025 AxonFlow
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

/**
 * Data types and models for the AxonFlow SDK.
 *
 * <p>This package contains request/response types for all AxonFlow API operations.
 *
 * <h2>Gateway Mode Types</h2>
 * <ul>
 *   <li>{@link com.getaxonflow.sdk.types.PolicyApprovalRequest} - Pre-check request</li>
 *   <li>{@link com.getaxonflow.sdk.types.PolicyApprovalResult} - Pre-check response</li>
 *   <li>{@link com.getaxonflow.sdk.types.AuditOptions} - Audit request</li>
 *   <li>{@link com.getaxonflow.sdk.types.AuditResult} - Audit response</li>
 * </ul>
 *
 * <h2>Proxy Mode Types</h2>
 * <ul>
 *   <li>{@link com.getaxonflow.sdk.types.ClientRequest} - Query request</li>
 *   <li>{@link com.getaxonflow.sdk.types.ClientResponse} - Query response</li>
 * </ul>
 *
 * <h2>Planning Types</h2>
 * <ul>
 *   <li>{@link com.getaxonflow.sdk.types.PlanRequest} - Plan generation request</li>
 *   <li>{@link com.getaxonflow.sdk.types.PlanResponse} - Generated plan</li>
 *   <li>{@link com.getaxonflow.sdk.types.PlanStep} - Individual plan step</li>
 * </ul>
 *
 * <h2>Connector Types</h2>
 * <ul>
 *   <li>{@link com.getaxonflow.sdk.types.ConnectorInfo} - Connector metadata</li>
 *   <li>{@link com.getaxonflow.sdk.types.ConnectorQuery} - Connector query request</li>
 *   <li>{@link com.getaxonflow.sdk.types.ConnectorResponse} - Connector query response</li>
 * </ul>
 */
package com.getaxonflow.sdk.types;
