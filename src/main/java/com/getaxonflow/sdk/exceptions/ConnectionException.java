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
 * Thrown when a connection to the AxonFlow API fails.
 *
 * <p>This typically occurs when:
 * <ul>
 *   <li>The AxonFlow Agent is not running</li>
 *   <li>Network connectivity issues</li>
 *   <li>DNS resolution failures</li>
 *   <li>SSL/TLS handshake errors</li>
 * </ul>
 */
public class ConnectionException extends AxonFlowException {

    private static final long serialVersionUID = 1L;

    private final String host;
    private final int port;

    /**
     * Creates a new ConnectionException.
     *
     * @param message the error message
     */
    public ConnectionException(String message) {
        super(message, 0, "CONNECTION_FAILED");
        this.host = null;
        this.port = 0;
    }

    /**
     * Creates a new ConnectionException with cause.
     *
     * @param message the error message
     * @param cause   the underlying cause
     */
    public ConnectionException(String message, Throwable cause) {
        super(message, 0, "CONNECTION_FAILED", cause);
        this.host = null;
        this.port = 0;
    }

    /**
     * Creates a new ConnectionException with connection details.
     *
     * @param message the error message
     * @param host    the target host
     * @param port    the target port
     * @param cause   the underlying cause
     */
    public ConnectionException(String message, String host, int port, Throwable cause) {
        super(message, 0, "CONNECTION_FAILED", cause);
        this.host = host;
        this.port = port;
    }

    /**
     * Returns the target host.
     *
     * @return the host, or null if not specified
     */
    public String getHost() {
        return host;
    }

    /**
     * Returns the target port.
     *
     * @return the port, or 0 if not specified
     */
    public int getPort() {
        return port;
    }
}
