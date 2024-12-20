package net.pincette.rs.multipart;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toMap;
import static net.pincette.util.Pair.pair;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import net.pincette.util.Array;
import net.pincette.util.Pair;

/**
 * @author Werner Donné
 */
class HeadersRecorder implements Recorder {
  private static final byte[] END = "\r\n\r\n".getBytes(US_ASCII);

  private final byte[] buffer = new byte[0xffff];
  private final Consumer<Map<String, String[]>> onCommit;
  private int position;

  HeadersRecorder(final Consumer<Map<String, String[]>> onCommit) {
    this.onCommit = onCommit;
  }

  private static Pair<String, String[]> splitLine(final String line) {
    final int index = line.indexOf(':');

    return index != -1
        ? pair(
            line.substring(0, index).trim(),
            stream(line.substring(index + 1).split(",")).map(String::trim).toArray(String[]::new))
        : null;
  }

  public void bufferDepleted() {
    // Not interested.
  }

  public void commit() {
    onCommit.accept(parseHeaders());
  }

  public boolean isCloseDelimiter() {
    return false;
  }

  public boolean isComplete() {
    return position > 3
        && Arrays.equals(END, 0, END.length, buffer, position - END.length, position);
  }

  public boolean next(final byte b) {
    if (position == buffer.length) {
      position = 0;
    }

    if (isComplete()) {
      return false;
    }

    buffer[position++] = b;

    return true;
  }

  public ByteBuffer rollback() {
    return null;
  }

  private Map<String, String[]> parseHeaders() {
    return new BufferedReader(
            new InputStreamReader(new ByteArrayInputStream(buffer, 0, position), UTF_8))
        .lines()
        .map(HeadersRecorder::splitLine)
        .filter(Objects::nonNull)
        .collect(toMap(pair -> pair.first, pair -> pair.second, Array::append));
  }
}
