/* Copyright 2013 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.onionoo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.torproject.onionoo.ResourceServlet.HttpServletRequestWrapper;
import org.torproject.onionoo.ResourceServlet.HttpServletResponseWrapper;

import com.google.gson.Gson;

public class ResourceServletTest {

  private static class ResourceServletTestHelper {

    private File tempOutDir;

    private SortedMap<String, String>
        relays = new TreeMap<String, String>(),
        bridges = new TreeMap<String, String>();

    private long lastModified = 1366806142000L; // 2013-04-24 12:22:22

    private boolean maintenanceMode = false;

    private class TestingHttpServletRequestWrapper
        extends HttpServletRequestWrapper {
      private String requestURI;
      private Map<String, String[]> parameterMap;
      private TestingHttpServletRequestWrapper(String requestURI,
          Map<String, String[]> parameterMap) {
        super(null);
        this.requestURI = requestURI;
        this.parameterMap = parameterMap == null
            ? new HashMap<String, String[]>() : parameterMap;
      }
      protected String getRequestURI() {
        return this.requestURI;
      }
      protected Map getParameterMap() {
        return this.parameterMap;
      }
      protected String[] getParameterValues(String parameterKey) {
        return this.parameterMap.get(parameterKey);
      }
    }

    private class TestingHttpServletResponseWrapper extends
        HttpServletResponseWrapper {
      private TestingHttpServletResponseWrapper() {
        super(null);
      }
      private int errorStatusCode;
      protected void sendError(int errorStatusCode) throws IOException {
        this.errorStatusCode = errorStatusCode;
      }
      private Map<String, String> headers = new HashMap<String, String>();
      protected void setHeader(String headerName, String headerValue) {
        this.headers.put(headerName, headerValue);
      }
      protected void setContentType(String contentType) {
      }
      protected void setCharacterEncoding(String characterEncoding) {
      }
      private StringWriter stringWriter;
      protected PrintWriter getWriter() throws IOException {
        if (this.stringWriter == null) {
          this.stringWriter = new StringWriter();
          return new PrintWriter(this.stringWriter);
        } else {
          throw new IOException("Can only request writer once");
        }
      }
      private String getWrittenContent() {
        return this.stringWriter == null ? null
            : this.stringWriter.toString();
      }
    }

    private TestingHttpServletRequestWrapper request;

    private TestingHttpServletResponseWrapper response;

    private String responseString;

    private SummaryDocument summaryDocument;

    private ResourceServletTestHelper(File tempOutDir, String requestURI,
        Map<String, String[]> parameterMap) {
      this.tempOutDir = tempOutDir;
      this.relays = new TreeMap<String, String>();
      this.relays.put("000C5F55", "r TorkaZ "
          + "000C5F55BD4814B917CC474BD537F1A3B33CCE2A "
          + "62.216.201.221;;62.216.201.222+62.216.201.223 "
          + "2013-04-19 05:00:00 9001 0 Running,Valid 20 de null -1 "
          + "reject 1-65535 2013-04-18 05:00:00 2013-04-19 05:00:00 "
          + "AS8767");
      this.relays.put("001C13B3", "r Ferrari458 "
          + "001C13B3A55A71B977CA65EC85539D79C653A3FC "
          + "68.38.171.200;[2001:4f8:3:2e::51]:9001; "
          + "2013-04-24 12:00:00 9001 9030 "
          + "Fast,Named,Running,V2Dir,Valid 1140 us "
          + "c-68-38-171-200.hsd1.pa.comcast.net 1366805763009 reject "
          + "1-65535 2013-02-12 16:00:00 2013-02-26 18:00:00 AS7922");
      this.relays.put("0025C136", "r TimMayTribute "
          + "0025C136C1F3A9EEFE2AE3F918F03BFA21B5070B 89.69.68.246;; "
          + "2013-04-22 20:00:00 9001 9030 "
          + "Fast,Running,Unnamed,V2Dir,Valid 63 a1 null -1 reject "
          + "1-65535 2013-04-16 18:00:00 2013-04-16 18:00:00 AS6830");
      this.bridges.put("0000831B", "b ec2bridgercc7f31fe "
          + "0000831B236DFF73D409AD17B40E2A728A53994F 10.199.7.176;; "
          + "2013-04-21 18:07:03 443 0 Valid -1 ?? null -1 null null "
          + "2013-04-20 15:37:04 null null null");
      this.bridges.put("0002D9BD", "b Unnamed "
          + "0002D9BDBBC230BD9C78FF502A16E0033EF87E0C 10.0.52.84;; "
          + "2013-04-20 17:37:04 443 0 Valid -1 ?? null -1 null "
          + "null 2013-04-14 07:07:05 null null null");
      this.bridges.put("0010D49C", "b gummy "
          + "1FEDE50ED8DBA1DD9F9165F78C8131E4A44AB756 10.63.169.98;; "
          + "2013-04-24 01:07:04 9001 0 Running,Valid -1 ?? null -1 null "
          + "null 2013-01-16 21:07:04 null null null");
      this.request = new TestingHttpServletRequestWrapper(requestURI,
          parameterMap);
      this.response = new TestingHttpServletResponseWrapper();
    }

    private void runTest() {
      try {
        this.writeSummaryFile();
        this.makeRequest();
        this.parseResponse();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    private void writeSummaryFile() throws IOException {
      File summaryFile = new File(this.tempOutDir, "summary");
      BufferedWriter bw = new BufferedWriter(new FileWriter(summaryFile));
      for (String relay : relays.values()) {
        bw.write(relay + "\n");
      }
      for (String bridge : bridges.values()) {
        bw.write(bridge + "\n");
      }
      bw.close();
      summaryFile.setLastModified(this.lastModified);
    }

    private void makeRequest() throws IOException {
      ResourceServlet rs = new ResourceServlet();
      rs.init(maintenanceMode, this.tempOutDir);
      rs.doGet(this.request, this.response);
    }

    private void parseResponse() {
      this.responseString = this.response.getWrittenContent();
      if (this.responseString != null) {
        Gson gson = new Gson();
        this.summaryDocument = gson.fromJson(this.responseString,
            SummaryDocument.class);
      }
    }

    private static void assertErrorStatusCode(File tempOutDir,
        String requestURI, int errorStatusCode) {
      ResourceServletTestHelper helper = new ResourceServletTestHelper(
          tempOutDir, requestURI, null);
      helper.runTest();
      assertEquals(errorStatusCode, helper.response.errorStatusCode);
    }

    private static void assertErrorStatusCode(File tempOutDir,
        String requestURI, String parameterKey, String[] parameterValues,
        int errorStatusCode) {
      Map<String, String[]> parameters = new HashMap<String, String[]>();
      parameters.put(parameterKey, parameterValues);
      ResourceServletTestHelper helper = new ResourceServletTestHelper(
          tempOutDir, requestURI, parameters);
      helper.runTest();
      assertEquals(errorStatusCode, helper.response.errorStatusCode);
    }

    private static void assertSummaryDocument(File tempOutDir,
        String requestURI, String parameterKey, String[] parameterValues,
        int expectedRelaysNumber, String[] expectedRelaysNicknames,
        int expectedBridgesNumber, String[] expectedBridgesNicknames) {
      Map<String, String[]> parameters = new HashMap<String, String[]>();
      parameters.put(parameterKey, parameterValues);
      ResourceServletTestHelper helper = new ResourceServletTestHelper(
          tempOutDir, requestURI, parameters);
      helper.runTest();
      assertNotNull(helper.summaryDocument);
      assertEquals(expectedRelaysNumber,
          helper.summaryDocument.relays.length);
      if (expectedRelaysNicknames != null) {
        for (int i = 0; i < expectedRelaysNumber; i++) {
          assertEquals(expectedRelaysNicknames[i],
              helper.summaryDocument.relays[i].n);
        }
      }
      assertEquals(expectedBridgesNumber,
          helper.summaryDocument.bridges.length);
      if (expectedBridgesNicknames != null) {
        for (int i = 0; i < expectedBridgesNumber; i++) {
          assertEquals(expectedBridgesNicknames[i],
              helper.summaryDocument.bridges[i].n);
        }
      }
    }
  }

  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();

  private File tempOutDir;

  @Before
  public void createTempOutDir() throws IOException {
    tempOutDir = this.tempFolder.newFolder("out");
  }

  private static class SummaryDocument {
    private String relays_published;
    private RelaySummary[] relays;
    private String bridges_published;
    private BridgeSummary[] bridges;
  }

  private static class RelaySummary {
    private String n;
    private String f;
    private String[] a;
    private boolean r;
  }

  private static class BridgeSummary {
    private String n;
    private String h;
    private boolean r;
  }

  @Test()
  public void testValidSummaryRelay() throws IOException {
    ResourceServletTestHelper helper = new ResourceServletTestHelper(
        this.tempOutDir, "/summary", null);
    helper.relays.clear();
    helper.relays.put("000C5F55", "r TorkaZ "
        + "000C5F55BD4814B917CC474BD537F1A3B33CCE2A 62.216.201.221;; "
        + "2013-04-19 05:00:00 9001 0 Running,Valid 20 de null -1 "
        + "reject 1-65535 2013-04-18 05:00:00 2013-04-19 05:00:00 "
        + "AS8767");
    helper.runTest();
    assertEquals("2013-04-19 05:00:00",
        helper.summaryDocument.relays_published);
    assertEquals(1, helper.summaryDocument.relays.length);
    RelaySummary relay = helper.summaryDocument.relays[0];
    assertEquals("TorkaZ", relay.n);
    assertEquals("000C5F55BD4814B917CC474BD537F1A3B33CCE2A", relay.f);
    assertEquals(1, relay.a.length);
    assertEquals("62.216.201.221", relay.a[0]);
    assertTrue(relay.r);
  }

  @Test()
  public void testValidSummaryBridge() {
    ResourceServletTestHelper helper = new ResourceServletTestHelper(
        this.tempOutDir, "/summary", null);
    helper.bridges.clear();
    helper.bridges.put("0000831", "b ec2bridgercc7f31fe "
        + "0000831B236DFF73D409AD17B40E2A728A53994F 10.199.7.176;; "
        + "2013-04-21 18:07:03 443 0 Valid -1 ?? null -1 null null "
        + "2013-04-20 15:37:04 null null null");
    helper.runTest();
    assertEquals("2013-04-21 18:07:03",
        helper.summaryDocument.bridges_published);
    assertEquals(1, helper.summaryDocument.bridges.length);
    BridgeSummary bridge = helper.summaryDocument.bridges[0];
    assertEquals("ec2bridgercc7f31fe", bridge.n);
    assertEquals("0000831B236DFF73D409AD17B40E2A728A53994F", bridge.h);
    assertFalse(bridge.r);
  }

  @Test()
  public void testNonExistantDocumentType() {
    ResourceServletTestHelper.assertErrorStatusCode(
        this.tempOutDir, "/doesnotexist", 400);
  }

  @Test()
  public void testSUMMARYDocument() {
    ResourceServletTestHelper.assertErrorStatusCode(
        this.tempOutDir, "/SUMMARY", 400);
  }

  @Test()
  public void testTypeRelay() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "type", new String[] { "relay" }, 3, null, 0, null);
  }

  @Test()
  public void testTypeBridge() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "type", new String[] { "bridge" }, 0, null, 3, null);
  }

  @Test()
  public void testTypeBridgerelay() {
    ResourceServletTestHelper.assertErrorStatusCode(this.tempOutDir,
        "/summary", "type", new String[] { "bridgerelay" }, 400);
  }

  @Test()
  public void testTypeRelayBridge() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "type", new String[] { "relay", "bridge" }, 3, null,
        0, null);
  }

  @Test()
  public void testTypeBridgeRelay() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "type", new String[] { "bridge", "relay" }, 0, null,
        3, null);
  }

  @Test()
  public void testTypeRelayRelay() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "type", new String[] { "relay", "relay" }, 3, null, 0,
        null);
  }

  @Test()
  public void testTYPERelay() {
    ResourceServletTestHelper.assertErrorStatusCode(this.tempOutDir,
        "/summary", "TYPE", new String[] { "relay" }, 400);
  }

  @Test()
  public void testTypeRELAY() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "type", new String[] { "RELAY" }, 3, null, 0, null);
  }

  @Test()
  public void testRunningTrue() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "running", new String[] { "true" }, 1,
        new String[] { "Ferrari458" }, 1, new String[] { "gummy" });
  }

  @Test()
  public void testRunningFalse() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "running", new String[] { "false" }, 2, null, 2,
        null);
  }

  @Test()
  public void testRunningTruefalse() {
    ResourceServletTestHelper.assertErrorStatusCode(this.tempOutDir,
        "/summary", "running", new String[] { "truefalse" }, 400);
  }

  @Test()
  public void testRunningTrueFalse() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "running", new String[] { "true", "false" }, 1,
        new String[] { "Ferrari458" }, 1,  new String[] { "gummy" });
  }

  @Test()
  public void testRunningFalseTrue() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "running", new String[] { "false", "true" }, 2, null,
        2, null);
  }

  @Test()
  public void testRunningTrueTrue() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "running", new String[] { "true", "true" }, 1,
        new String[] { "Ferrari458" }, 1, new String[] { "gummy" });
  }

  @Test()
  public void testRUNNINGTrue() {
    ResourceServletTestHelper.assertErrorStatusCode(this.tempOutDir,
        "/summary", "RUNNING", new String[] { "true" }, 400);
  }

  @Test()
  public void testRunningTRUE() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "running", new String[] { "TRUE" }, 1, null, 1, null);
  }

  @Test()
  public void testSearchTorkaZ() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "search", new String[] { "TorkaZ" }, 1,
        new String[] { "TorkaZ" }, 0, null);
  }

  @Test()
  public void testSearchTorkaX() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "search", new String[] { "TorkaX" }, 0, null, 0,
        null);
  }

  @Test()
  public void testSearchOrkaZ() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "search", new String[] { "orkaZ" }, 1,
        new String[] { "TorkaZ" }, 0, null);
  }

  @Test()
  public void testSearchTorka() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "search", new String[] { "Torka" }, 1,
        new String[] { "TorkaZ" }, 0, null);
  }

  @Test()
  public void testSearchTORKAZ() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "search", new String[] { "TORKAZ" }, 1,
        new String[] { "TorkaZ" }, 0, null);
  }

  @Test()
  public void testSearchDollarFingerprint() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "search",
        new String[] { "$000C5F55BD4814B917CC474BD537F1A3B33CCE2A" }, 1,
        new String[] { "TorkaZ" }, 0, null);
  }

  @Test()
  public void testSearchFingerprint() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "search",
        new String[] { "000C5F55BD4814B917CC474BD537F1A3B33CCE2A" }, 1,
        new String[] { "TorkaZ" }, 0, null);
  }

  @Test()
  public void testSearchDollarFingerprint39() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "search",
        new String[] { "$000C5F55BD4814B917CC474BD537F1A3B33CCE2" }, 1,
        new String[] { "TorkaZ" }, 0, null);
  }

  @Test()
  public void testSearchDollarFingerprintLowerCase39() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "search",
        new String[] { "$000c5f55bd4814b917cc474bd537f1a3b33cce2" }, 1,
        new String[] { "TorkaZ" }, 0, null);
  }

  @Test()
  public void testSearchFingerprintLowerCase39() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "search",
        new String[] { "000c5f55bd4814b917cc474bd537f1a3b33cce2" }, 1,
        new String[] { "TorkaZ" }, 0, null);
  }

  @Test()
  public void testSearchDollarHashedFingerprint() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "search",
        new String[] { "$5aa14c08d62913e0057a9ad5863b458c0ce94cee" }, 1,
        new String[] { "TorkaZ" }, 0, null);
  }

  @Test()
  public void testSearchDollarHashedFingerprint39() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "search",
        new String[] { "$5aa14c08d62913e0057a9ad5863b458c0ce94ce" }, 1,
        new String[] { "TorkaZ" }, 0, null);
  }

  @Test()
  public void testSearchDollarHashedFingerprint41() {
    ResourceServletTestHelper.assertErrorStatusCode(this.tempOutDir,
        "/summary", "search",
        new String[] { "$5aa14c08d62913e0057a9ad5863b458c0ce94ceee" },
        400);
  }

  @Test()
  public void testSearchIp() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "search", new String[] { "62.216.201.221" }, 1,
        new String[] { "TorkaZ" }, 0, null);
  }

  @Test()
  public void testSearchIp24Network() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "search", new String[] { "62.216.201" }, 1,
        new String[] { "TorkaZ" }, 0, null);
  }

  @Test()
  public void testSearchIpExit() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "search", new String[] { "62.216.201.222" }, 1,
        new String[] { "TorkaZ" }, 0, null);
  }

  @Test()
  public void testSearchIpv6() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "search", new String[] { "[2001:4f8:3:2e::51]" }, 1,
        new String[] { "Ferrari458" }, 0, null);
  }

  @Test()
  public void testSearchIpv6Slash64() {
    /* TODO This request should return one bridge. */
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "search", new String[] { "[2001:4f8:3:2e::]" }, 0,
        null, 0, null);
  }

  @Test()
  public void testSearchIpv6Uncompressed() {
    /* TODO This request should return one bridge. */
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "search",
        new String[] { "[2001:04f8:0003:002e:0000:0000:0000:0051]" }, 0,
        null, 0, null);
  }

  @Test()
  public void testSearchIpv6UpperCase() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "search", new String[] { "[2001:4F8:3:2E::51]" }, 1,
        new String[] { "Ferrari458" }, 0, null);
  }

  @Test()
  public void testSearchIpv6ThreeColons() {
    /* TODO This request should fail with a 400 status code, because the
     * given IPv6 address is invalid. */
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "search", new String[] { "[2001:4f8:3:2e:::51]" }, 0,
        null, 0, null);
  }

  @Test()
  public void testSearchIpv6FiveHex() {
    /* TODO This request should fail with a 400 status code, because the
     * given IPv6 address is invalid. */
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "search", new String[] { "[20014:f80:3:2e::51]" }, 0,
        null, 0, null);
  }

  @Test()
  public void testSearchIpv6NineGroups() {
    /* TODO This request should fail with a 400 status code, because the
     * given IPv6 address is invalid. */
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "search", new String[] { "[1:2:3:4:5:6:7:8:9]" }, 0,
        null, 0, null);
  }

  @Test()
  public void testSearchIpv6TcpPort() {
    ResourceServletTestHelper.assertErrorStatusCode(this.tempOutDir,
        "/summary", "search", new String[] { "[2001:4f8:3:2e::51]:9001" },
        400);
  }

  @Test()
  public void testSearchGummy() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "search", new String[] { "gummy" }, 0, null, 1,
        new String[] { "gummy" });
  }

  @Test()
  public void testSearchGummi() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "search", new String[] { "gummi" }, 0, null, 0, null);
  }

  @Test()
  public void testSearchUmmy() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "search", new String[] { "ummy" }, 0, null, 1,
        new String[] { "gummy" });
  }

  @Test()
  public void testSearchGumm() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "search", new String[] { "gumm" }, 0, null, 1,
        new String[] { "gummy" });
  }

  @Test()
  public void testSearchGUMMY() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "search", new String[] { "GUMMY" }, 0, null, 1,
        new String[] { "gummy" });
  }

  @Test()
  public void testSearchBridgeDollarHashedFingerprint() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "search",
        new String[] { "$1FEDE50ED8DBA1DD9F9165F78C8131E4A44AB756" }, 0,
        null, 1, new String[] { "gummy" });
  }

  @Test()
  public void testSearchBridgeHashedFingerprint() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "search",
        new String[] { "1FEDE50ED8DBA1DD9F9165F78C8131E4A44AB756" }, 0,
        null, 1, new String[] { "gummy" });
  }

  @Test()
  public void testSearchBridgeDollarHashedFingerprint39() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "search",
        new String[] { "$1FEDE50ED8DBA1DD9F9165F78C8131E4A44AB75" }, 0,
        null, 1, new String[] { "gummy" });
  }

  @Test()
  public void testSearchBridgeDollarHashedFingerprintLowerCase39() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "search",
        new String[] { "$1fede50ed8dba1dd9f9165f78c8131e4a44ab75" }, 0,
        null, 1, new String[] { "gummy" });
  }

  @Test()
  public void testSearchBridgeHashedFingerprintLowerCase39() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "search",
        new String[] { "1fede50ed8dba1dd9f9165f78c8131e4a44ab75" }, 0,
        null, 1, new String[] { "gummy" });
  }

  @Test()
  public void testSearchBridgeDollarHashedHashedFingerprint() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "search",
        new String[] { "$CE52F898DB3678BCE33FAC28C92774DE90D618B5" }, 0,
        null, 1, new String[] { "gummy" });
  }

  @Test()
  public void testSearchBridgeDollarHashedHashedFingerprint39() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "search",
        new String[] { "$CE52F898DB3678BCE33FAC28C92774DE90D618B" }, 0,
        null, 1, new String[] { "gummy" });
  }

  @Test()
  public void testSearchBridgeDollarOriginalFingerprint() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "search",
        new String[] { "$0010D49C6DA1E46A316563099F41BFE40B6C7183" }, 0,
        null, 0, null);
  }

  @Test()
  public void testSearchUnderscore() {
    ResourceServletTestHelper.assertErrorStatusCode(this.tempOutDir,
        "/summary", "search", new String[] { "_" }, 400);
  }

  @Test()
  public void testLookupFingerprint() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "lookup",
        new String[] { "000C5F55BD4814B917CC474BD537F1A3B33CCE2A" }, 1,
        new String[] { "TorkaZ" }, 0, null);
  }

  @Test()
  public void testLookupDollarFingerprint() {
    ResourceServletTestHelper.assertErrorStatusCode(this.tempOutDir,
        "/summary", "lookup",
        new String[] { "$000C5F55BD4814B917CC474BD537F1A3B33CCE2A" },
        400);
  }

  @Test()
  public void testLookupDollarFingerprint39() {
    ResourceServletTestHelper.assertErrorStatusCode(this.tempOutDir,
        "/summary", "lookup",
        new String[] { "$000C5F55BD4814B917CC474BD537F1A3B33CCE2" }, 400);
  }

  @Test()
  public void testLookupFingerprintLowerCase39() {
    ResourceServletTestHelper.assertErrorStatusCode(this.tempOutDir,
        "/summary", "lookup",
        new String[] { "000c5f55bd4814b917cc474bd537f1a3b33cce2" }, 400);
  }

  @Test()
  public void testLookupHashedFingerprint() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "lookup",
        new String[] { "5aa14c08d62913e0057a9ad5863b458c0ce94cee" }, 1,
        new String[] { "TorkaZ" }, 0, null);
  }

  @Test()
  public void testLookupBridgeHashedFingerprint() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "lookup",
        new String[] { "1FEDE50ED8DBA1DD9F9165F78C8131E4A44AB756" }, 0,
        null, 1, new String[] { "gummy" });
  }

  @Test()
  public void testSearchBridgeHashedHashedFingerprint() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "lookup",
        new String[] { "CE52F898DB3678BCE33FAC28C92774DE90D618B5" }, 0,
        null, 1, new String[] { "gummy" });
  }

  @Test()
  public void testLookupBridgeOriginalFingerprint() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "lookup",
        new String[] { "0010D49C6DA1E46A316563099F41BFE40B6C7183" }, 0,
        null, 0, null);
  }

  @Test()
  public void testCountryDe() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "country", new String[] { "de" }, 1,
        new String[] { "TorkaZ" }, 0, null);
  }

  @Test()
  public void testCountryFr() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "country", new String[] { "fr" }, 0, null, 0, null);
  }

  @Test()
  public void testCountryZz() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "country", new String[] { "zz" }, 0, null, 0, null);
  }

  @Test()
  public void testCountryDE() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "country", new String[] { "DE" }, 1,
        new String[] { "TorkaZ" }, 0, null);
  }

  @Test()
  public void testCountryDeu() {
    ResourceServletTestHelper.assertErrorStatusCode(this.tempOutDir,
        "/summary", "country", new String[] { "deu" }, 400);
  }

  @Test()
  public void testCountryD() {
    ResourceServletTestHelper.assertErrorStatusCode(this.tempOutDir,
        "/summary", "country", new String[] { "d" }, 400);
  }

  @Test()
  public void testCountryA1() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "country", new String[] { "a1" }, 1,
        new String[] { "TimMayTribute" }, 0, null);
  }

  @Test()
  public void testCountryDeDe() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "country", new String[] { "de", "de" }, 1,
        new String[] { "TorkaZ" }, 0, null);
  }

  @Test()
  public void testAsAS8767() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "as", new String[] { "AS8767" }, 1,
        new String[] { "TorkaZ" }, 0, null);
  }

  @Test()
  public void testAs8767() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "as", new String[] { "8767" }, 1,
        new String[] { "TorkaZ" }, 0, null);
  }

  @Test()
  public void testAsAS() {
    ResourceServletTestHelper.assertErrorStatusCode(this.tempOutDir,
        "/summary", "as", new String[] { "AS" }, 400);
  }

  @Test()
  public void testAsas8767() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "as", new String[] { "as8767" }, 1,
        new String[] { "TorkaZ" }, 0, null);
  }

  @Test()
  public void testAsASSpace8767() {
    ResourceServletTestHelper.assertErrorStatusCode(this.tempOutDir,
        "/summary", "as", new String[] { "AS 8767" }, 400);
  }

  @Test()
  public void testFlagRunning() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "flag", new String[] { "Running" }, 3, null, 0, null);
  }

  @Test()
  public void testFlagValid() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "flag", new String[] { "Valid" }, 3, null, 0, null);
  }

  @Test()
  public void testFlagFast() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "flag", new String[] { "Fast" }, 2, null, 0, null);
  }

  @Test()
  public void testFlagNamed() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "flag", new String[] { "Named" }, 1, null, 0, null);
  }

  @Test()
  public void testFlagUnnamed() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "flag", new String[] { "Unnamed" }, 1, null, 0, null);
  }

  @Test()
  public void testFlagV2Dir() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "flag", new String[] { "V2Dir" }, 2, null, 0, null);
  }

  @Test()
  public void testFlagGuard() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "flag", new String[] { "Guard" }, 0, null, 0, null);
  }

  @Test()
  public void testFlagCool() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "flag", new String[] { "Cool" }, 0, null, 0, null);
  }

  @Test()
  public void testFirstSeenDaysZeroToTwo() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "first_seen_days", new String[] { "0-2" }, 0, null, 0,
        null);
  }

  @Test()
  public void testFirstSeenDaysUpToThree() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "first_seen_days", new String[] { "-3" }, 0, null, 1,
        null);
  }

  @Test()
  public void testFirstSeenDaysThree() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "first_seen_days", new String[] { "3" }, 0, null, 1,
        null);
  }

  @Test()
  public void testFirstSeenDaysTwoToFive() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "first_seen_days", new String[] { "2-5" }, 0, null, 1,
        null);
  }

  @Test()
  public void testFirstSeenDaysSixToSixteen() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "first_seen_days", new String[] { "6-16" }, 2, null,
        1, null);
  }

  @Test()
  public void testFirstSeenDaysNinetysevenOrMore() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "first_seen_days", new String[] { "97-" }, 0, null, 1,
        null);
  }

  @Test()
  public void testFirstSeenDaysNinetyeightOrMore() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "first_seen_days", new String[] { "98-" }, 0, null, 0,
        null);
  }

  @Test()
  public void testFirstSeenDaysDashDash() {
    ResourceServletTestHelper.assertErrorStatusCode(this.tempOutDir,
        "/summary", "first_seen_days", new String[] { "--" }, 400);
  }

  @Test()
  public void testFirstSeenDaysDashOneDash() {
    ResourceServletTestHelper.assertErrorStatusCode(this.tempOutDir,
        "/summary", "first_seen_days", new String[] { "-1-" }, 400);
  }

  @Test()
  public void testFirstSeenDaysZeroDotDotOne() {
    ResourceServletTestHelper.assertErrorStatusCode(this.tempOutDir,
        "/summary", "first_seen_days", new String[] { "0..1" }, 400);
  }

  @Test()
  public void testFirstSeenDaysElevenDigits() {
    ResourceServletTestHelper.assertErrorStatusCode(this.tempOutDir,
        "/summary", "first_seen_days", new String[] { "12345678901" },
        400);
  }

  @Test()
  public void testFirstSeenDaysLargeTenDigitNumber() {
    ResourceServletTestHelper.assertErrorStatusCode(this.tempOutDir,
        "/summary", "first_seen_days", new String[] { "9999999999" },
        400);
  }

  @Test()
  public void testFirstSeenDaysMaxInt() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "last_seen_days",
        new String[] { String.valueOf(Integer.MAX_VALUE) }, 0, null, 0,
        null);
  }

  @Test()
  public void testFirstSeenDaysMaxIntPlusOne() {
    ResourceServletTestHelper.assertErrorStatusCode(this.tempOutDir,
        "/summary", "first_seen_days",
        new String[] { String.valueOf(Integer.MAX_VALUE + 1) }, 400);
  }

  @Test()
  public void testLastSeenDaysZero() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "last_seen_days", new String[] { "0" }, 1, null, 1,
        null);
  }

  @Test()
  public void testLastSeenDaysUpToZero() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "last_seen_days", new String[] { "-0" }, 1, null, 1,
        null);
  }

  @Test()
  public void testLastSeenDaysOneToThree() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "last_seen_days", new String[] { "1-3" }, 1, null, 2,
        null);
  }

  @Test()
  public void testLastSeenDaysSixOrMore() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "last_seen_days", new String[] { "6-" }, 0, null, 0,
        null);
  }

  @Test()
  public void testOrderConsensusWeightAscending() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "order", new String[] { "consensus_weight" }, 3,
        new String[] { "TorkaZ", "TimMayTribute", "Ferrari458" }, 3,
        null);
  }

  @Test()
  public void testOrderConsensusWeightDescending() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "order", new String[] { "-consensus_weight" }, 3,
        new String[] { "Ferrari458", "TimMayTribute", "TorkaZ" }, 3,
        null);
  }

  @Test()
  public void testOrderConsensusWeightAscendingTwice() {
    ResourceServletTestHelper.assertErrorStatusCode(this.tempOutDir,
        "/summary", "order",
        new String[] { "consensus_weight,consensus_weight" }, 400);
  }

  @Test()
  public void testOrderConsensusWeightAscendingThenDescending() {
    ResourceServletTestHelper.assertErrorStatusCode(this.tempOutDir,
        "/summary", "order",
        new String[] { "consensus_weight,-consensus_weight" }, 400);
  }

  @Test()
  public void testOrderConsensusWeightThenNickname() {
    ResourceServletTestHelper.assertErrorStatusCode(this.tempOutDir,
        "/summary", "order", new String[] { "consensus_weight,nickname" },
        400);
  }

  @Test()
  public void testOrderCONSENSUS_WEIGHT() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "order", new String[] { "CONSENSUS_WEIGHT" }, 3,
        new String[] { "TorkaZ", "TimMayTribute", "Ferrari458" }, 3,
        null);
  }

  @Test()
  public void testOffsetOne() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "offset", new String[] { "1" }, 2, null, 3, null);
  }

  @Test()
  public void testOffsetAllRelays() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "offset", new String[] { "3" }, 0, null, 3, null);
  }

  @Test()
  public void testOffsetAllRelaysAndOneBridge() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "offset", new String[] { "4" }, 0, null, 2, null);
  }

  @Test()
  public void testOffsetAllRelaysAndAllBridges() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "offset", new String[] { "6" }, 0, null, 0, null);
  }

  @Test()
  public void testOffsetMoreThanAllRelaysAndAllBridges() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "offset", new String[] { "7" }, 0, null, 0, null);
  }

  @Test()
  public void testOffsetZero() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "offset", new String[] { "0" }, 3, null, 3, null);
  }

  @Test()
  public void testOffsetMinusOne() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "offset", new String[] { "-1" }, 3, null, 3, null);
  }

  @Test()
  public void testOffsetOneWord() {
    ResourceServletTestHelper.assertErrorStatusCode(this.tempOutDir,
        "/summary", "offset", new String[] { "one" }, 400);
  }

  @Test()
  public void testLimitOne() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "limit", new String[] { "1" }, 1, null, 0, null);
  }

  @Test()
  public void testLimitAllRelays() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "limit", new String[] { "3" }, 3, null, 0, null);
  }

  @Test()
  public void testLimitAllRelaysAndOneBridge() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "limit", new String[] { "4" }, 3, null, 1, null);
  }

  @Test()
  public void testLimitAllRelaysAndAllBridges() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "limit", new String[] { "6" }, 3, null, 3, null);
  }

  @Test()
  public void testLimitMoreThanAllRelaysAndAllBridges() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "limit", new String[] { "7" }, 3, null, 3, null);
  }

  @Test()
  public void testLimitZero() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "limit", new String[] { "0" }, 0, null, 0, null);
  }

  @Test()
  public void testLimitMinusOne() {
    ResourceServletTestHelper.assertSummaryDocument(this.tempOutDir,
        "/summary", "limit", new String[] { "-1" }, 0, null, 0, null);
  }

  @Test()
  public void testLimitOneWord() {
    ResourceServletTestHelper.assertErrorStatusCode(this.tempOutDir,
        "/summary", "limit", new String[] { "one" }, 400);
  }
}

