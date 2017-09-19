package org.apache.manifoldcf.agents.output.bfsioutput;


import org.apache.commons.io.IOUtils;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Properties;

public class AlfrescoBfsiOutputAgent {

    private static Properties properties1 = new Properties();

    /**
     * No-arguments constructor.
     */
    public AlfrescoBfsiOutputAgent() {
    }

    /**
     * Get traditional properties in name=value format.
     *
     * @param filePathAndName Path and name of properties file (without the
     *                        .properties extension).
     * @return Properties read in from provided file.
     */
    public Properties loadTraditionalProperties(final String filePathAndName) {
        final Properties properties = new Properties();
        try {
            final FileInputStream in = new FileInputStream(filePathAndName);
            properties.load(in);
            in.close();
        } catch (FileNotFoundException fnfEx) {
            System.err.println("Could not read properties from file " + filePathAndName);
        } catch (IOException ioEx) {
            System.err.println(
                    "IOException encountered while reading from " + filePathAndName);
        }
        return properties;
    }


    /**
     * Get traditional properties in name=value format.
     *
     * @param fileName name of the file target for meta-data manifest creation
     * @return String execution log

    public static String createBulkManifest(final String fileName, Properties properties, Path files_deployment_location) {
        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        Calendar cal = Calendar.getInstance();
        String date = dateFormat.format(cal.getTime());
        FileOutputStream outStream = null;
        String metaDatafileName = fileName + ".metadata.properties.xml";
        String metaDatafilePath = files_deployment_location.toString() + "/" + metaDatafileName;
        try {
            outStream = new FileOutputStream(metaDatafilePath);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        if (!(properties == null)) { // If the method was invoqued with properties object use it
            createMetaDataXmlFile(properties, outStream);
        } else { // else write default properties
            properties1.setProperty("type", "cm:content");
            properties1.setProperty("aspects", "cm:versionable,cm:dublincore");
            properties1.setProperty("cm:title", "Crawled document from ManifoldCF Input Connectors : " + date);
            properties1.setProperty("cm:description", "");
            properties1.setProperty("cm:author", "Apache ManifoldCF BulkFilesystem Output Connector");
            properties1.setProperty("cm:publisher", "BulkFilesystem Output Connector");
            properties1.setProperty("cm:contributor", "BulkFilesystem Output Connector");
            properties1.setProperty("cm:type", "default_plus_dubincore_aspect");
            properties1.setProperty("cm:identifier", fileName);
            properties1.setProperty("cm:source", "BulkFilesystem Output Connector");
            properties1.setProperty("cm:coverage", "General");
            properties1.setProperty("cm:rights", "");
            properties1.setProperty("cm:subject", "Metadata file created with Apache ManifoldCF BulkFilesystem Output Connector");
            createMetaDataXmlFile(properties1, outStream);
        }
        return "Created Manifest for " + fileName + ": " + files_deployment_location.toString() + "/" + metaDatafileName;
    }*/



    /**
     * Get traditional properties in name=value format.
     *
     * @param fileName name of the file target for meta-data manifest creation
     * @return String execution log
     */
    public static String createBulkDocument(final InputStream fileIS, final String fileName, Properties properties, Path files_deployment_location) {



        File targetFile = new File(files_deployment_location.toString() + "/" + fileName);

        try {
            java.nio.file.Files.copy(
                    fileIS,
                    targetFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            e.printStackTrace();
        }
        IOUtils.closeQuietly(fileIS);

        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        Calendar cal = Calendar.getInstance();
        String date = dateFormat.format(cal.getTime());
        FileOutputStream outStream = null;
        String metaDatafileName = fileName + ".metadata.properties.xml";
        String metaDatafilePath = files_deployment_location.toString() + "/" + metaDatafileName;
        try {
            outStream = new FileOutputStream(metaDatafilePath);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        if (!(properties == null)) { // If the method was invoqued with properties object use it
            createMetaDataXmlFile(properties, outStream);
        } else { // else write default properties
            properties1.setProperty("type", "cm:content");
            properties1.setProperty("aspects", "cm:versionable,cm:dublincore");
            properties1.setProperty("cm:title", "Crawled document from ManifoldCF Input Connectors : " + date);
            properties1.setProperty("cm:description", "");
            properties1.setProperty("cm:author", "Apache ManifoldCF BulkFilesystem Output Connector");
            properties1.setProperty("cm:publisher", "BulkFilesystem Output Connector");
            properties1.setProperty("cm:contributor", "BulkFilesystem Output Connector");
            properties1.setProperty("cm:type", "default_plus_dubincore_aspect");
            properties1.setProperty("cm:identifier", fileName);
            properties1.setProperty("cm:source", "BulkFilesystem Output Connector");
            properties1.setProperty("cm:coverage", "General");
            properties1.setProperty("cm:rights", "");
            properties1.setProperty("cm:subject", "Metadata file created with Apache ManifoldCF BulkFilesystem Output Connector");
            createMetaDataXmlFile(properties1, outStream);
        }
        return "Created Manifest for " + fileName + ": " + files_deployment_location.toString() + "/" + metaDatafileName;
    }



    public static void createMetaDataXmlFile(final Properties sourceProperties, final OutputStream out) {

        try {
            sourceProperties.storeToXML(out, "To use with Alfresco in-place-bulk importer");
        } catch (IOException ioEx) {
            System.err.println("ERROR trying to store properties in XML!");
        }
    }

    /**
     * Store provided properties in XML format.
     *
     * @param sourceProperties Properties to be stored in XML format.
     * @param out              OutputStream to which to write XML formatted properties.
     */
    public void storeXmlProperties(final Properties sourceProperties, final OutputStream out) {
        try {
            sourceProperties.storeToXML(out, "This is easy!");
        } catch (IOException ioEx) {
            System.err.println("ERROR trying to store properties in XML!");
        }
    }

    /**
     * Store provided properties in XML format to provided file.
     *
     * @param sourceProperties Properties to be stored in XML format.
     * @param pathAndFileName  Path and name of file to which XML-formatted
     *                         properties will be written.
     */
    public void storeXmlPropertiesToFile(
            final Properties sourceProperties,
            final String pathAndFileName) {
        try {
            FileOutputStream fos = new FileOutputStream(pathAndFileName);
            storeXmlProperties(sourceProperties, fos);
            fos.close();
        } catch (FileNotFoundException fnfEx) {
            System.err.println("ERROR writing to " + pathAndFileName);
        } catch (IOException ioEx) {
            System.err.println(
                    "ERROR trying to write XML properties to file " + pathAndFileName);
        }
    }
}