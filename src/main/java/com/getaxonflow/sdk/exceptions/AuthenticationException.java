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
 * Thrown when authentication with the AxonFlow API fails.
 *
 * <p>This typically occurs when:
 * <ul>
 *   <li>The license key is invalid or expired</li>
 *   <li>The client ID/secret combination is incorrect</li>
 *   <li>The API key has been revoked</li>
 * </ul>
 */
public class AuthenticationException extends AxonFlowException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new AuthenticationException.
     *
     * @param message the error message
     */
    public AuthenticationException(String message) {
        super(message, 401, "AUTHENTICATION_FAILED");
    }

    /**
     * Creates a new AuthenticationException with a cause.
     *
     * @param message the error message
     * @param cause   the underlying cause
     */
    public AuthenticationException(String message, Throwable cause) {
        super(message, 401, "AUTHENTICATION_FAILED", cause);
    }

    /**
     * Creates a new AuthenticationException with a custom status code.
     *
     * @param message    the error message
     * @param statusCode the HTTP status code
     */
    public AuthenticationException(String message, int statusCode) {
        super(message, statusCode, "AUTHENTICATION_FAILED");
    }
}
