/* Copyright 2011, 2012 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo.cron;

import java.io.File;

import org.torproject.onionoo.docs.DocumentStore;
import org.torproject.onionoo.updater.BandwidthStatusUpdater;
import org.torproject.onionoo.updater.ClientsStatusUpdater;
import org.torproject.onionoo.updater.DescriptorSource;
import org.torproject.onionoo.updater.LookupService;
import org.torproject.onionoo.updater.NodeDetailsStatusUpdater;
import org.torproject.onionoo.updater.ReverseDomainNameResolver;
import org.torproject.onionoo.updater.StatusUpdater;
import org.torproject.onionoo.updater.UptimeStatusUpdater;
import org.torproject.onionoo.updater.WeightsStatusUpdater;
import org.torproject.onionoo.util.ApplicationFactory;
import org.torproject.onionoo.util.LockFile;
import org.torproject.onionoo.util.Logger;
import org.torproject.onionoo.writer.BandwidthDocumentWriter;
import org.torproject.onionoo.writer.ClientsDocumentWriter;
import org.torproject.onionoo.writer.DetailsDocumentWriter;
import org.torproject.onionoo.writer.DocumentWriter;
import org.torproject.onionoo.writer.SummaryDocumentWriter;
import org.torproject.onionoo.writer.UptimeDocumentWriter;
import org.torproject.onionoo.writer.WeightsDocumentWriter;

/* Update search data and status data files. */
public class Main {

  private Main() {
  }

  public static void main(String[] args) {

    LockFile lf = new LockFile();
    Logger.setTime();
    Logger.printStatus("Initializing.");
    if (lf.acquireLock()) {
      Logger.printStatusTime("Acquired lock");
    } else {
      Logger.printErrorTime("Could not acquire lock.  Is Onionoo "
          + "already running?  Terminating");
      return;
    }

    DescriptorSource dso = ApplicationFactory.getDescriptorSource();
    Logger.printStatusTime("Initialized descriptor source");
    DocumentStore ds = ApplicationFactory.getDocumentStore();
    Logger.printStatusTime("Initialized document store");
    LookupService ls = new LookupService(new File("geoip"));
    Logger.printStatusTime("Initialized Geoip lookup service");
    ReverseDomainNameResolver rdnr = new ReverseDomainNameResolver();
    Logger.printStatusTime("Initialized reverse domain name resolver");
    NodeDetailsStatusUpdater ndsu = new NodeDetailsStatusUpdater(rdnr,
        ls);
    Logger.printStatusTime("Initialized node data writer");
    BandwidthStatusUpdater bsu = new BandwidthStatusUpdater();
    Logger.printStatusTime("Initialized bandwidth status updater");
    WeightsStatusUpdater wsu = new WeightsStatusUpdater();
    Logger.printStatusTime("Initialized weights status updater");
    ClientsStatusUpdater csu = new ClientsStatusUpdater();
    Logger.printStatusTime("Initialized clients status updater");
    UptimeStatusUpdater usu = new UptimeStatusUpdater();
    Logger.printStatusTime("Initialized uptime status updater");
    StatusUpdater[] sus = new StatusUpdater[] { ndsu, bsu, wsu, csu,
        usu };

    SummaryDocumentWriter sdw = new SummaryDocumentWriter();
    Logger.printStatusTime("Initialized summary document writer");
    DetailsDocumentWriter ddw = new DetailsDocumentWriter();
    Logger.printStatusTime("Initialized details document writer");
    BandwidthDocumentWriter bdw = new BandwidthDocumentWriter();
    Logger.printStatusTime("Initialized bandwidth document writer");
    WeightsDocumentWriter wdw = new WeightsDocumentWriter();
    Logger.printStatusTime("Initialized weights document writer");
    ClientsDocumentWriter cdw = new ClientsDocumentWriter();
    Logger.printStatusTime("Initialized clients document writer");
    UptimeDocumentWriter udw = new UptimeDocumentWriter();
    Logger.printStatusTime("Initialized uptime document writer");
    DocumentWriter[] dws = new DocumentWriter[] { sdw, ddw, bdw, wdw, cdw,
        udw };

    Logger.printStatus("Downloading descriptors.");
    dso.downloadDescriptors();

    Logger.printStatus("Reading descriptors.");
    dso.readDescriptors();

    Logger.printStatus("Updating internal status files.");
    for (StatusUpdater su : sus) {
      su.updateStatuses();
      Logger.printStatusTime(su.getClass().getSimpleName()
          + " updated status files");
    }

    Logger.printStatus("Updating document files.");
    for (DocumentWriter dw : dws) {
      dw.writeDocuments();
    }

    Logger.printStatus("Shutting down.");
    dso.writeHistoryFiles();
    Logger.printStatusTime("Wrote parse histories");
    ds.flushDocumentCache();
    Logger.printStatusTime("Flushed document cache");

    Logger.printStatus("Gathering statistics.");
    for (StatusUpdater su : sus) {
      String statsString = su.getStatsString();
      if (statsString != null) {
        Logger.printStatistics(su.getClass().getSimpleName(),
            statsString);
      }
    }
    for (DocumentWriter dw : dws) {
      String statsString = dw.getStatsString();
      if (statsString != null) {
        Logger.printStatistics(dw.getClass().getSimpleName(),
            statsString);
      }
    }
    Logger.printStatistics("Descriptor source", dso.getStatsString());
    Logger.printStatistics("Document store", ds.getStatsString());
    Logger.printStatistics("GeoIP lookup service", ls.getStatsString());
    Logger.printStatistics("Reverse domain name resolver",
        rdnr.getStatsString());

    Logger.printStatus("Releasing lock.");
    if (lf.releaseLock()) {
      Logger.printStatusTime("Released lock");
    } else {
      Logger.printErrorTime("Could not release lock.  The next "
          + "execution may not start as expected");
    }

    Logger.printStatus("Terminating.");
  }
}
