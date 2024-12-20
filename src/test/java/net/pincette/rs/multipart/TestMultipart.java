package net.pincette.rs.multipart;

import static java.nio.ByteBuffer.wrap;
import static java.nio.channels.FileChannel.open;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.SYNC;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.util.Objects.deepEquals;
import static java.util.Objects.requireNonNull;
import static net.pincette.io.StreamConnector.copy;
import static net.pincette.rs.Chain.with;
import static net.pincette.rs.ReadableByteChannelPublisher.readableByteChannel;
import static net.pincette.rs.Util.join;
import static net.pincette.rs.WritableByteChannelSubscriber.writableByteChannel;
import static net.pincette.util.Collections.map;
import static net.pincette.util.Collections.put;
import static net.pincette.util.Collections.remove;
import static net.pincette.util.Pair.pair;
import static net.pincette.util.StreamUtil.rangeInclusive;
import static net.pincette.util.Util.tryToDoRethrow;
import static net.pincette.util.Util.tryToGetRethrow;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.pincette.rs.Source;
import net.pincette.util.Pair;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TestMultipart {
  private static final String BOUNDARY = "2982c546-0d24-4738-b21c-116fc18819cd";
  private static final Map<String, String[]> HEADERS =
      map(
          pair("Header1", new String[] {"Value"}),
          pair("Header2", new String[] {"Value1", "Value2"}));

  private static BodyPart bodyPart(final File file, final int bufferSize) {
    return tryToGetRethrow(
            () ->
                new BodyPart(
                    put(HEADERS, "Filename", new String[] {outFile(file).getAbsolutePath()}),
                    readableByteChannel(open(file.toPath(), READ), true, bufferSize)))
        .orElse(null);
  }

  private static void checkHeaders(final List<Map<String, String[]>> headers) {
    headers.forEach(h -> assertTrue(equals(HEADERS, remove(h, "Filename"))));
  }

  private static void compareFiles(final List<Pair<File, File>> files) {
    files.forEach(pair -> assertArrayEquals(read(pair.first), read(pair.second)));
  }

  private static File copyResource(final String resource) {
    final File file = new File("/tmp" + resource);

    tryToDoRethrow(
        () ->
            copy(
                requireNonNull(TestMultipart.class.getResourceAsStream(resource)),
                new FileOutputStream(requireNonNull(file))));

    return file;
  }

  private static boolean equals(final Map<String, String[]> m1, final Map<String, String[]> m2) {
    return m1 != null
        && m2 != null
        && m1.size() == m2.size()
        && m1.entrySet().stream().allMatch(e -> deepEquals(e.getValue(), m2.get(e.getKey())));
  }

  private static List<Pair<File, File>> files() {
    return rangeInclusive(1, 4)
        .map(i -> copyResource("/file" + i))
        .map(f -> pair(f, outFile(f)))
        .toList();
  }

  private static File outFile(final File inFile) {
    return new File(inFile.getAbsolutePath() + ".out");
  }

  private static byte[] read(final File file) {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();

    tryToDoRethrow(() -> copy(new FileInputStream(file), out));

    return out.toByteArray();
  }

  private static void removeFiles(final List<Pair<File, File>> files) {
    files.forEach(
        pair -> {
          pair.first.delete();
          pair.second.delete();
        });
  }

  private void test(final int bufferSize, final byte[] extra, final boolean transportPadding) {
    final List<Pair<File, File>> files = files();
    final List<Map<String, String[]>> headers = new ArrayList<>();

    try {
      join(
          with(Source.of(
                  files.stream()
                      .map(pair -> pair.first)
                      .map(file -> bodyPart(file, bufferSize))
                      .toList()))
              .map(new MultipartEncoder(BOUNDARY, transportPadding))
              .before(() -> wrap(extra != null ? extra : new byte[0]))
              .after(() -> wrap(extra != null ? extra : new byte[0]))
              .map(new MultipartDecoder(BOUNDARY))
              .map(
                  bodyPart -> {
                    headers.add(bodyPart.headers());
                    System.out.println(new File(bodyPart.headers().get("Filename")[0]).getName());

                    tryToDoRethrow(
                        () ->
                            bodyPart
                                .body()
                                .subscribe(
                                    writableByteChannel(
                                        open(
                                            new File(bodyPart.headers().get("Filename")[0])
                                                .toPath(),
                                            CREATE,
                                            WRITE,
                                            SYNC))));

                    return bodyPart;
                  })
              .buffer(1) // Backpressure violation test.
              .get());

      checkHeaders(headers);
      compareFiles(files);
    } finally {
      removeFiles(files);
    }
  }

  @Test
  @DisplayName("test1")
  void test1() {
    test(1024, null, false);
  }

  @Test
  @DisplayName("test2")
  void test2() {
    test(0xffffff, null, false);
  }

  @Test
  @DisplayName("test3")
  void test3() {
    test(1024, "sfgbdfggsdg".getBytes(US_ASCII), false);
  }

  @Test
  @DisplayName("test4")
  void test4() {
    test(1024, null, true);
  }
}
