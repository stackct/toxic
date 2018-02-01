package toxic.util

class Tail {
  private static final int BLOCK_SIZE = 64 * 1024;

  /**
   * Send the last numLines lines of a text file to an ouputstream.
   * @param fileName The name of the file to tail
   * @param numLines The number of lines to display from the end of the file
   * @param out The writer to write to
   */
  public static void tailFile(String fileName, long numLines, Writer out)
      throws IOException {
    RandomAccessFile file = new RandomAccessFile(fileName, "r");

    try {
      final long fileSize = file.length();
      long blockStart = Math.max(0, fileSize - BLOCK_SIZE);
      byte[] buff = new byte[BLOCK_SIZE];
      long lineCount = 0;
      long startOffset = -1; // will be set when line is found
      while (lineCount < numLines) {
        // Read a block of data
        file.seek(blockStart);
        int bytesRead = file.read(buff);
        for (int i = bytesRead - 1; i >= 0; i--) {
          if (buff[i] == '\n') {
            lineCount++;
            if (lineCount == numLines) {
              startOffset = blockStart + i;
              break;
            }
          }
        }
        // set next block starting point
        if (blockStart > 0 || lineCount == numLines) {
          blockStart = Math.max(0, blockStart - BLOCK_SIZE);
        } else {
          startOffset = 0;
          break;
        }
      }

      // Start at the offset we found for the desired line number
      // and write the file to the output stream
      file.seek(startOffset);
      int bytesRead = 0;
      while ((bytesRead = file.read(buff)) != -1) {
        String s = new String(buff, 0, bytesRead);
        out.write(s);
      }
      out.flush();
    } finally {
      try { file.close() } catch(Exception e){}
    }
  }
}