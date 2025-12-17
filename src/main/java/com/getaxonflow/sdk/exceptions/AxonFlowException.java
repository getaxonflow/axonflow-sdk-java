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
package com.getaxonflow.sdk.exceptions;

/**
 * Base exception for all AxonFlow SDK errors.
 *
 * <p>All exceptions thrown by the AxonFlow SDK extend this class, allowing
 * callers to catch all SDK-related errors with a single catch block.
 *
 * <p>Example usage:
 * <pre>{@code
 * try {
 *     PolicyApprovalResult result = axonflow.getPolicyApprovedContext(request);
 * } catch (PolicyViolationException e) {
 *     // Handle policy violation specifically
 *     System.out.println("Blocked by: " + e.getPolicyName());
 * } catch (AxonFlowException e) {
 *     // Handle all other SDK errors
 *     System.out.println("Error: " + e.getMessage());
 * }
 * }</pre>
 */
public class AxonFlowException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int statusCode;
    private final String errorCode;

    /**
     * Creates a new AxonFlowException with a message.
     *
     * @param message the error message
     */
    public AxonFlowException(String message) {
        super(message);
        this.statusCode = 0;
        this.errorCode = null;
    }

    /**
     * Creates a new AxonFlowException with a message and cause.
     *
     * @param message the error message
     * @param cause   the underlying cause
     */
    public AxonFlowException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
        this.errorCode = null;
    }

    /**
     * Creates a new AxonFlowException with full details.
     *
     * @param message    the error message
     * @param statusCode the HTTP status code (if applicable)
     * @param errorCode  the error code (if applicable)
     */
    public AxonFlowException(String message, int statusCode, String errorCode) {
        super(message);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
    }

    /**
     * Creates a new AxonFlowException with full details and cause.
     *
     * @param message    the error message
     * @param statusCode the HTTP status code (if applicable)
     * @param errorCode  the error code (if applicable)
     * @param cause      the underlying cause
     */
    public AxonFlowException(String message, int statusCode, String errorCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
    }

    /**
     * Returns the HTTP status code associated with this error.
     *
     * @return the HTTP status code, or 0 if not applicable
     */
    public int getStatusCode() {
        return statusCode;
    }

    /**
     * Returns the error code from the API.
     *
     * @return the error code, or null if not available
     */
    public String getErrorCode() {
        return errorCode;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(getClass().getSimpleName());
        sb.append(": ").append(getMessage());
        if (statusCode > 0) {
            sb.append(" (status=").append(statusCode).append(")");
        }
        if (errorCode != null) {
            sb.append(" [").append(errorCode).append("]");
        }
        return sb.toString();
    }
}
