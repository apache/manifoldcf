package org.apache.manifoldcf.crawler.connectors.csv;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.stream.Stream;

public class CSVUtils {

  public static String[] getColumnsLabel(final String csvFilePath, final String separator) throws FileNotFoundException, IOException {
    final File csvFile = new File(csvFilePath);
    if (csvFile.exists() && csvFile.canRead()) {
      try (FileReader fr = new FileReader(csvFile); BufferedReader br = new BufferedReader(fr);) {
        final String firstLine = br.readLine();
        final String[] columnsLabel = firstLine.split(separator);
        return columnsLabel;
      }
    } else {
      throw new IOException("Cannot read file");
    }
  }

  public static long getCSVLinesNumber(final String csvFilePath) throws IOException {
    final File csvFile = new File(csvFilePath);
    long numberOfLines;
    try (Stream<String> lines = Files.lines(csvFile.toPath(), StandardCharsets.UTF_8);) {
      numberOfLines = lines.count();
    }
    return numberOfLines;
  }

}
