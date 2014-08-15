/* Copyright 2014 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo.docs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.SortedMap;
import java.util.TreeMap;

import org.junit.Before;
import org.junit.Test;
import org.torproject.descriptor.BandwidthHistory;
import org.torproject.onionoo.DummyTime;
import org.torproject.onionoo.util.TimeFactory;

public class BandwidthStatusTest {

  private long currentTimeMillis = DateTimeHelper.parse(
      "2014-08-01 02:22:22");

  @Before
  public void createDummyTime() {
    TimeFactory.setTime(new DummyTime(this.currentTimeMillis));
  }

  @Test()
  public void testEmptyStatusNotDirty() {
    BandwidthStatus bandwidthStatus = new BandwidthStatus();
    assertFalse("Newly created bandwidth status should not be dirty.",
        bandwidthStatus.isDirty());
  }

  private static class DummyBandwidthHistory implements BandwidthHistory {
    DummyBandwidthHistory(String line) {
      this.line = line;
      String[] parts = line.split(" ");
      this.historyEndMillis = DateTimeHelper.parse(parts[1] + " "
          + parts[2]);
      this.intervalLength = Long.parseLong(parts[3].substring(1));
      long intervalEndMillis = this.historyEndMillis,
          intervalLengthMillis = this.intervalLength * 1000L;
      String[] valueStrings = parts[5].split(",");
      for (int i = valueStrings.length - 1; i >= 0; i--) {
        this.bandwidthValues.put(intervalEndMillis,
            Long.parseLong(valueStrings[i]));
        intervalEndMillis -= intervalLengthMillis;
      }
    }
    private String line;
    public String getLine() {
      return this.line;
    }
    private long historyEndMillis;
    public long getHistoryEndMillis() {
      return this.historyEndMillis;
    }
    private long intervalLength;
    public long getIntervalLength() {
      return this.intervalLength;
    }
    private SortedMap<Long, Long> bandwidthValues =
        new TreeMap<Long, Long>();
    public SortedMap<Long, Long> getBandwidthValues() {
      return this.bandwidthValues;
    }
  }

  @Test()
  public void testNewStatusWithSingleInterval() {
    BandwidthStatus bandwidthStatus = new BandwidthStatus();
    bandwidthStatus.addToWriteHistory(new DummyBandwidthHistory(
        "write-history 2014-08-01 00:22:22 (900 s) 30720"));
    assertTrue("Updated bandwidth status should be marked as dirty.",
        bandwidthStatus.isDirty());
    assertEquals("Formatted document string not as expected.",
        "w 2014-08-01 00:07:22 2014-08-01 00:22:22 30720\n",
        bandwidthStatus.toDocumentString());
    bandwidthStatus.clearDirty();
    assertFalse("Dirty flag should be cleared.",
        bandwidthStatus.isDirty());
  }

  @Test()
  public void testNewStatusWithTwoIntervals() {
    BandwidthStatus bandwidthStatus = new BandwidthStatus();
    bandwidthStatus.addToWriteHistory(new DummyBandwidthHistory(
        "write-history 2014-08-01 00:22:22 (900 s) 4096,30720"));
    assertEquals("Formatted document string not as expected.",
        "w 2014-07-31 23:52:22 2014-08-01 00:07:22 4096\n"
        + "w 2014-08-01 00:07:22 2014-08-01 00:22:22 30720\n",
        bandwidthStatus.toDocumentString());
  }

  @Test()
  public void testExistingStatusWithNewIntervals() {
    BandwidthStatus bandwidthStatus = new BandwidthStatus();
    String existingLines =
        "w 2014-07-31 23:52:22 2014-08-01 00:07:22 4096\n"
        + "w 2014-08-01 00:07:22 2014-08-01 00:22:22 30720\n";
    bandwidthStatus.fromDocumentString(existingLines);
    bandwidthStatus.addToWriteHistory(new DummyBandwidthHistory(
        "write-history 2014-08-01 00:37:22 (900 s) 0"));
    assertEquals("New interval should be appended.",
        existingLines + "w 2014-08-01 00:22:22 2014-08-01 00:37:22 0\n",
        bandwidthStatus.toDocumentString());
  }

  @Test()
  public void testCompressRecentIntervals() {
    BandwidthStatus bandwidthStatus = new BandwidthStatus();
    String existingLines =
        "w 2014-08-01 00:07:22 2014-08-01 00:22:22 30720\n"
        + "w 2014-08-01 00:22:22 2014-08-01 00:37:22 4096\n";
    bandwidthStatus.fromDocumentString(existingLines);
    bandwidthStatus.compressHistory();
    assertEquals("Two recent intervals should not be compressed.",
        existingLines, bandwidthStatus.toDocumentString());
  }

  @Test()
  public void testCompressOldIntervals() {
    BandwidthStatus bandwidthStatus = new BandwidthStatus();
    bandwidthStatus.fromDocumentString(
        "w 2013-08-01 00:07:22 2013-08-01 00:22:22 30720\n"
        + "w 2013-08-01 00:22:22 2013-08-01 00:37:22 4096\n");
    bandwidthStatus.compressHistory();
    assertEquals("Two old intervals should be compressed into one.",
        "w 2013-08-01 00:07:22 2013-08-01 00:37:22 34816\n",
        bandwidthStatus.toDocumentString());
  }

  @Test()
  public void testCompressOldIntervalsOverMonthEnd() {
    BandwidthStatus bandwidthStatus = new BandwidthStatus();
    String statusLines =
        "w 2013-07-31 23:52:22 2013-08-01 00:07:22 4096\n"
        + "w 2013-08-01 00:07:22 2013-08-01 00:22:22 30720\n";
    bandwidthStatus.fromDocumentString(statusLines);
    bandwidthStatus.compressHistory();
    assertEquals("Two old intervals should not be merged over month end.",
        statusLines, bandwidthStatus.toDocumentString());
  }
}
