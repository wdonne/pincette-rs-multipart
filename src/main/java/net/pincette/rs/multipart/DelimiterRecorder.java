package net.pincette.rs.multipart;

import static java.nio.charset.StandardCharsets.US_ASCII;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * @author Werner Donné
 */
class DelimiterRecorder implements Recorder {
  private final byte[] boundary;
  private final byte[] buffer;
  private final byte[] closingBoundary;
  private int boundaryPosition;
  private boolean inPadding;
  private int position;

  DelimiterRecorder(final String boundary) {
    this.boundary = ("\r\n--" + boundary + "\r\n").getBytes(US_ASCII);
    closingBoundary = ("\r\n--" + boundary + "--").getBytes(US_ASCII);
    buffer = new byte[2 * closingBoundary.length]; // Give some room for padding but not too much.
  }

  private static boolean isLineEnd(final byte b) {
    return b == (byte) '\r' || b == (byte) '\n';
  }

  private static boolean isPadding(final byte b) {
    return b == (byte) ' ' || b == (byte) '\t';
  }

  public void bufferDepleted() {
    // Not interested.
  }

  public void commit() {
    // Nothing to do.
  }

  public boolean isCloseDelimiter() {
    return position == closingBoundary.length
        && Arrays.equals(closingBoundary, 0, closingBoundary.length, buffer, 0, position);
  }

  public boolean isComplete() {
    return (boundaryPosition == closingBoundary.length && buffer[position - 1] == (byte) '-')
        || (boundaryPosition == boundary.length && buffer[position - 1] == (byte) '\n');
  }

  public boolean next(final byte b) {
    buffer[position++] = b;

    final boolean isFirstPadding = isPadding(b) && position == boundary.length - 1;

    if (!inPadding && isFirstPadding) {
      inPadding = true;
    }

    final boolean isPadding = inPadding && isPadding(b);

    if (!isPadding) {
      ++boundaryPosition;
    }

    return (inPadding && (isPadding || isLineEnd(b)))
        || b == boundary[boundaryPosition - 1]
        || b == closingBoundary[boundaryPosition - 1];
  }

  public ByteBuffer rollback() {
    return ByteBuffer.wrap(buffer, 0, position);
  }
}
