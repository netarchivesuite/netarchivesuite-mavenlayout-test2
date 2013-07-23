/* File:        $Id$
 * Revision:    $Revision$
 * Author:      $Author$
 * Date:        $Date$
 *
 * The Netarchive Suite - Software to harvest and preserve websites
 * Copyright 2004-2012 The Royal Danish Library, the Danish State and
 * University Library, the National Library of France and the Austrian
 * National Library.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package dk.netarkivet.harvester.tools;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;

import dk.netarkivet.common.CommonSettings;
import dk.netarkivet.common.Constants;
import dk.netarkivet.common.distribute.JMSConnectionFactory;
import dk.netarkivet.common.distribute.arcrepository.ArcRepositoryClientFactory;
import dk.netarkivet.common.distribute.arcrepository.BatchStatus;
import dk.netarkivet.common.distribute.arcrepository.ViewerArcRepositoryClient;
import dk.netarkivet.common.exceptions.IOFailure;
import dk.netarkivet.common.exceptions.NetarkivetException;
import dk.netarkivet.common.tools.SimpleCmdlineTool;
import dk.netarkivet.common.tools.ToolRunnerBase;
import dk.netarkivet.common.utils.FileUtils;
import dk.netarkivet.common.utils.Settings;
import dk.netarkivet.common.utils.SystemUtils;
import dk.netarkivet.common.utils.batch.FileBatchJob;
import dk.netarkivet.common.utils.cdx.ArchiveExtractCDXJob;
import dk.netarkivet.common.utils.cdx.CDXRecord;
import dk.netarkivet.harvester.HarvesterSettings;
import dk.netarkivet.harvester.harvesting.ArchiveFileNamingFactory;
import dk.netarkivet.harvester.harvesting.ArchiveFilenameParser;
import dk.netarkivet.harvester.harvesting.HarvestDocumentation;
import dk.netarkivet.harvester.harvesting.metadata.MetadataFileWriter;

/**
 * This tool creates a CDX metadata file for a given job's harvestPrefix by running a
 * batch job on the bitarchive and processing the results to give a metadata
 * file. Use option -w to select WARC output, and -a to select ARC output:
 * If no option available, then warc mode is selected
 *
 * Usage: java dk.netarkivet.harvester.tools.CreateCDXMetadataFile -w harvestPrefix
 * Usage: java dk.netarkivet.harvester.tools.CreateCDXMetadataFile -a harvestPrefix
 * 
 *
 */
public class CreateCDXMetadataFile extends ToolRunnerBase {
    
    public static final String ARCMODE = "arc";
    public static final String WARCMODE = "warc";
    
    /** Main method.  Creates and runs the tool object responsible for
     * batching over the bitarchive and creating a metadata file for a job.
     *
     * @param argv Arguments to the tool: jobID
     */
    public static void main(String[] argv) {
        new CreateCDXMetadataFile().runTheTool(argv);
    }

    /** Create the tool instance.
     *
     * @return A new tool object.
     */
    protected SimpleCmdlineTool makeMyTool() {
        return new CreateCDXMetadataFileTool();
    }

    /** The actual tool object that creates CDX files.
     */
    private static class CreateCDXMetadataFileTool
            implements SimpleCmdlineTool {
        /** Write output mode. Is it arc or warc mode. */
        private boolean isWarcOutputMode;
        /** Which jobId to process. */
        private long jobId;
        
        /** The connection to the arc repository. */
        private ViewerArcRepositoryClient arcrep;
        /** The file pattern that matches an ARC or WARC file name without the jobID.
         * If combined with a jobID, this will match filenames like
         * 42-117-20051212141240-00001-sb-test-har-001.statsbiblioteket.dk.arc
         */
        private static final String REMAINING_ARCHIVE_FILE_PATTERN =
                "-\\d+-\\d+-\\d+-.*";

        /** Check that a valid jobID were given.  This does not check whether
         * jobs actually exist for that ID.
         *
         * @param args The args given on the command line.
         * @return True if the args are legal.
         */
        public boolean checkArgs(String... args) { 
            Options options = new Options();
            options.addOption("a", false, "write an metadata ARC file");
            options.addOption("w", false, "write an metadata WARC file");
            
            CommandLineParser parser = new PosixParser();
            try {
                CommandLine cl = parser.parse(options, args);
                if (cl.hasOption('a') && cl.hasOption('w')) {
                    System.err.println("Option 'a' and option 'w' cannot be used at the same time");
                    return false;
                }
                if (cl.hasOption('a')) {
                    this.isWarcOutputMode = false;
                } else { // default mode is 'warc' : don't need to check for option -w
                    this.isWarcOutputMode = true;
                }
            } catch (ParseException e) {
                System.err.println("Unable to parse arguments: " + e);
                return false;
            }
            
            if (args.length < 1) {
                System.err.println("Missing arguments");
                return false;
            }
            
            // Assume that last argument is the jobId
            int jobIdIndex = args.length - 1;
            try {
                this.jobId = Long.parseLong(args[jobIdIndex]); 
                if (this.jobId < 1) {
                    System.err.println("" + args[jobIdIndex]
                            + " is not a valid job ID");
                    return false;
                }
                 
                return true;
            } catch (NumberFormatException e) {
                System.err.println("'" + args[jobIdIndex] + "' is not a valid job ID"); 
                return false;
            }
        }

        /**
         * Create required resources here (the ArcRepositoryClient instance).
         * Resources created here should be released in tearDown, which is
         * guaranteed to be run.
         *
         * @param args The arguments that were given on the command line
         * (not used here)
         */
        public void setUp(String... args) {
            arcrep = ArcRepositoryClientFactory.getViewerInstance();
        }

        /**
         * Closes all resources we are using, which is only the
         * ArcRepositoryClient.  This is guaranteed to be called at shutdown.
         */
        public void tearDown() {
            if (arcrep != null) {
                arcrep.close();
            }
            JMSConnectionFactory.getInstance().cleanup();
        }

        /** The workhorse method of this tool: Runs the batch job,
         * copies the result, then turns the result into a proper
         * metadata file.
         * This method assumes that the args have already been read 
         * by the checkArgs method, and thus jobId has been parsed, 
         * and the isWarcOutputMode established
         *
         * @param args Arguments given on the command line.
         */
        public void run(String... args) {
            final long jobID = this.jobId;
            FileBatchJob job = new ArchiveExtractCDXJob();
            Settings.set(HarvesterSettings.METADATA_FORMAT, (isWarcOutputMode)? "warc": "arc");
            final String filePattern = jobID + REMAINING_ARCHIVE_FILE_PATTERN;
            
            System.out.println("Creating cdx-" + ((isWarcOutputMode)? "warcfile": "arcfile")  
            + " from file matching pattern: " + filePattern);
            job.processOnlyFilesMatching(filePattern);
            
            BatchStatus status = arcrep.batch(
                    job, Settings.get(CommonSettings.USE_REPLICA_ID));
            if (status.hasResultFile()) {
                System.out.println("Got results from archive. Processing data");
                File resultFile = null;
                try {
                    resultFile = File.createTempFile("extract-batch", ".cdx",
                            FileUtils.getTempDir());
                    resultFile.deleteOnExit();
                    status.copyResults(resultFile);                    
                    arcifyResultFile(resultFile, jobID);
                } catch (IOException e) {
                    throw new IOFailure("Error getting results for job "
                            + jobID, e);
                } finally {
                    if (resultFile != null) {
                        FileUtils.remove(resultFile);
                    }
                }
            } else {
                System.err.println("Got new results from archive. Program ending now");
            }
        }

        /** Turns a raw CDX file for the given jobID into a metadatafile
         * containing the CDX lines in one archive record per each ARC or WARC file indexed.
         * The output is put into a file called &lt;jobID&gt;-metadata-1.arc.
         *
         * @param resultFile The CDX file returned by a ExtractCDXJob for the
         * given jobID.
         * @param jobID The jobID we work on.
         * @throws IOException If an I/O error occurs, or the resultFile
         * does not exist
         */
        private void arcifyResultFile(File resultFile, long jobID)
                throws IOException {
            BufferedReader reader
                    = new BufferedReader(new FileReader(resultFile));
            
            File outputFile = new File(MetadataFileWriter.getMetadataArchiveFileName(
                    Long.toString(jobID)));
            System.out.println("Writing cdx to file '" + outputFile.getAbsolutePath() + "'.");
            try {
                MetadataFileWriter writer = MetadataFileWriter.createWriter(
                        outputFile);
                try {
                    String line;
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    String lastFilename = null;
                    ArchiveFilenameParser parser = null;
                    while ((line = reader.readLine()) != null) {
                        // parse filename out of line
                        parser = parseLine(line, jobID);
                        if (parser == null) { // Bad line, try the next
                            continue;
                        }
                        if (!parser.getFilename().equals(lastFilename)) {
                            // When we reach the end of a block of lines from
                            // one ARC file, we write those as a single entry.
                            writeCDXEntry(writer, parser, baos.toByteArray());
                            baos.reset();
                            lastFilename = parser.getFilename();
                        }
                        baos.write(line.getBytes());
                        baos.write("\n".getBytes());
                    }
                    if (parser != null) {
                        writeCDXEntry(writer, parser, baos.toByteArray());
                    }
                } finally {
                    writer.close();
                }
            } finally {
                reader.close();
            }
        }

        /** Utility method to parse out the parts of a CDX line.
         * If a different jobID is found in the CDX line than we're given,
         * or the CDX line is unparsable, we print an error message and return
         * null, expecting processing to continue.
         *
         * @param line The line to parse.
         * @param jobID The job we're working on.
         * @return An object containing the salient parts of the filename
         * of the ARC file as mentioned in the given CDX line, or null if
         * the filename didn't match the job we're working on.
         */
        private ArchiveFilenameParser parseLine(String line, long jobID) {
            try {
                String filename = new CDXRecord(line).getArcfile();
                ArchiveFilenameParser filenameParser = 
                        ArchiveFileNamingFactory.getInstance()
                            .getArchiveFilenameParser(new File(filename));
                if (!filenameParser.getJobID().equals(Long.toString(jobID))) {
                    System.err.println("Found entry for job "
                            + filenameParser.getJobID() + " while looking for "
                            + jobID + " in " + line);
                    return null;
                }
                return filenameParser;
            } catch (NetarkivetException e) {
                System.err.println("Error parsing CDX line '" + line + "': "
                        + e);
                return null;
            }
        }

        /** Writes a full entry of CDX files to the ARCWriter.
         *
         * @param writer The writer we're currently writing to.
         * @param parser The filename of all the entries stored in baos.  This
         * is used to generate the URI for the entry.
         * @param bytes The bytes of the CDX records to be written under this
         * entry.
         * @throws IOFailure if the write fails for any reason
         */
        private void writeCDXEntry(MetadataFileWriter writer,
                                   ArchiveFilenameParser parser,
                                   byte[] bytes)
                throws IOFailure {
            try {
                writer.write(HarvestDocumentation.getCDXURI(
                        parser.getHarvestID(), parser.getJobID(),
                        parser.getTimeStamp(), parser.getSerialNo()).toString(),
                        Constants.CDX_MIME_TYPE,
                        SystemUtils.getLocalIP(),
                        System.currentTimeMillis(),
                        bytes);
            } catch (IOException e) {
                throw new IOFailure("Failed to write ARC/WARC entry with CDX lines "
                        + "for " + parser.getFilename(), e);
            }
        }

        /** Return a string describing the parameters accepted by the
         * CreateCDXMetadataFile tool.
         *
         * @return String with description of parameters.
         */
        public String listParameters() {
            return "[-a|w] harvestPrefix";
        }
    }
}
