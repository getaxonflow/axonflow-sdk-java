/*
 * Copyright 2026 AxonFlow
 * Licensed under the Apache License, Version 2.0.
 *
 * Tests for classifyEndpoint (issue #1525).
 */
package com.getaxonflow.sdk.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TelemetryEndpointTypeTest {

  // ---- localhost ----

  @Test
  @DisplayName("localhost: hostname")
  void localhostHostname() {
    assertEquals("localhost", TelemetryReporter.classifyEndpoint("http://localhost:8080"));
    assertEquals("localhost", TelemetryReporter.classifyEndpoint("https://localhost"));
  }

  @Test
  @DisplayName("localhost: 127.0.0.1")
  void localhostIPv4() {
    assertEquals("localhost", TelemetryReporter.classifyEndpoint("http://127.0.0.1"));
    assertEquals("localhost", TelemetryReporter.classifyEndpoint("http://127.0.0.1:8080"));
  }

  @Test
  @DisplayName("localhost: 127/8")
  void localhost127Eight() {
    assertEquals("localhost", TelemetryReporter.classifyEndpoint("http://127.1.2.3"));
  }

  @Test
  @DisplayName("localhost: IPv6 ::1 with brackets")
  void localhostIPv6() {
    assertEquals("localhost", TelemetryReporter.classifyEndpoint("http://[::1]"));
    assertEquals("localhost", TelemetryReporter.classifyEndpoint("http://[::1]:8080"));
  }

  @Test
  @DisplayName("localhost: 0.0.0.0")
  void localhostZero() {
    assertEquals("localhost", TelemetryReporter.classifyEndpoint("http://0.0.0.0:8080"));
  }

  @Test
  @DisplayName("localhost: *.localhost")
  void localhostSubdomain() {
    assertEquals("localhost", TelemetryReporter.classifyEndpoint("http://agent.localhost"));
  }

  @Test
  @DisplayName("localhost: case insensitive")
  void localhostCaseInsensitive() {
    assertEquals("localhost", TelemetryReporter.classifyEndpoint("http://LOCALHOST"));
  }

  // ---- private_network ----

  @Test
  @DisplayName("private: RFC1918 10.x")
  void privateRFC1918Ten() {
    assertEquals("private_network", TelemetryReporter.classifyEndpoint("http://10.0.0.1"));
    assertEquals("private_network", TelemetryReporter.classifyEndpoint("http://10.1.2.3"));
  }

  @Test
  @DisplayName("private: RFC1918 192.168.x")
  void privateRFC1918OneNineTwo() {
    assertEquals("private_network", TelemetryReporter.classifyEndpoint("http://192.168.1.1"));
  }

  @Test
  @DisplayName("private: RFC1918 172.16-31")
  void privateRFC1918OneSevenTwo() {
    assertEquals("private_network", TelemetryReporter.classifyEndpoint("http://172.16.0.1"));
    assertEquals("private_network", TelemetryReporter.classifyEndpoint("http://172.31.255.254"));
  }

  @Test
  @DisplayName("private: boundary 172.15 and 172.32 NOT private")
  void privateRFC1918Boundary() {
    assertEquals("remote", TelemetryReporter.classifyEndpoint("http://172.15.0.1"));
    assertEquals("remote", TelemetryReporter.classifyEndpoint("http://172.32.0.1"));
  }

  @Test
  @DisplayName("private: link-local 169.254")
  void privateLinkLocal() {
    assertEquals("private_network", TelemetryReporter.classifyEndpoint("http://169.254.169.254"));
  }

  @Test
  @DisplayName("private: hostname suffixes")
  void privateHostnameSuffixes() {
    assertEquals("private_network", TelemetryReporter.classifyEndpoint("http://agent.internal"));
    assertEquals("private_network", TelemetryReporter.classifyEndpoint("http://agent.local"));
    assertEquals("private_network", TelemetryReporter.classifyEndpoint("http://agent.lan"));
    assertEquals("private_network", TelemetryReporter.classifyEndpoint("http://agent.intranet"));
  }

  @Test
  @DisplayName("private: case insensitive .internal")
  void privateCaseInsensitive() {
    assertEquals("private_network", TelemetryReporter.classifyEndpoint("http://AGENT.INTERNAL"));
  }

  // ---- remote ----

  @Test
  @DisplayName("remote: public hostnames")
  void remotePublicHostname() {
    assertEquals(
        "remote", TelemetryReporter.classifyEndpoint("https://production-us.getaxonflow.com"));
    assertEquals("remote", TelemetryReporter.classifyEndpoint("https://api.example.com"));
  }

  @Test
  @DisplayName("remote: public IPv4")
  void remotePublicIPv4() {
    assertEquals("remote", TelemetryReporter.classifyEndpoint("http://8.8.8.8"));
    assertEquals("remote", TelemetryReporter.classifyEndpoint("http://1.1.1.1"));
  }

  // ---- unknown ----

  @Test
  @DisplayName("unknown: empty")
  void unknownEmpty() {
    assertEquals("unknown", TelemetryReporter.classifyEndpoint(""));
  }

  @Test
  @DisplayName("unknown: null")
  void unknownNull() {
    assertEquals("unknown", TelemetryReporter.classifyEndpoint(null));
  }

  @Test
  @DisplayName("unknown: malformed")
  void unknownMalformed() {
    assertEquals("unknown", TelemetryReporter.classifyEndpoint("not-a-url"));
  }

  // ---- payload does not leak URL ----

  @Test
  @DisplayName("payload does not contain raw URL")
  void payloadDoesNotLeakURL() {
    String secret = "https://my-private-cluster.banking-internal.example.com:8443";
    String type = TelemetryReporter.classifyEndpoint(secret);
    assertEquals("remote", type);
    String json = TelemetryReporter.buildPayload("production", null, type);
    assertFalse(json.contains("my-private-cluster"), "payload leaked hostname");
    assertFalse(json.contains("banking-internal"), "payload leaked domain");
    assertFalse(json.contains("8443"), "payload leaked port");
    assertFalse(json.contains("https://"), "payload leaked scheme");
  }
}
