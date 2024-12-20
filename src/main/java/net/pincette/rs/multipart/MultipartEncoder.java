package net.pincette.rs.multipart;

import static java.lang.String.join;
import static java.nio.ByteBuffer.wrap;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.stream.Collectors.joining;
import static net.pincette.rs.After.after;
import static net.pincette.rs.Flatten.flatten;
import static net.pincette.rs.Mapper.map;
import static net.pincette.rs.Pipe.pipe;

import java.nio.ByteBuffer;
import java.util.concurrent.Flow.Publisher;
import java.util.function.Function;
import net.pincette.rs.Concat;
import net.pincette.rs.Delegate;
import net.pincette.rs.Source;

/**
 * Converts a stream of MIME multipart body parts into a byte stream.
 *
 * @author Werner Donné
 */
public class MultipartEncoder extends Delegate<BodyPart, ByteBuffer> {
  MultipartEncoder(final String boundary, final boolean transportPadding) {
    super(
        pipe(map(publishBodyPart(boundary, transportPadding)))
            .then(flatten())
            .then(
                after(
                    wrap(
                        ("\r\n--" + boundary + "--" + (transportPadding ? " \t" : ""))
                            .getBytes(US_ASCII)))));
  }

  public MultipartEncoder(final String boundary) {
    this(boundary, false);
  }

  private static byte[] headers(final BodyPart bodyPart) {
    return (bodyPart.headers().entrySet().stream()
                .map(e -> e.getKey() + ": " + join(",", e.getValue()))
                .collect(joining("\r\n"))
            + "\r\n\r\n")
        .getBytes(US_ASCII);
  }

  private static Function<BodyPart, Publisher<ByteBuffer>> publishBodyPart(
      final String boundary, final boolean transportPadding) {
    final byte[] b =
        ("\r\n--" + boundary + (transportPadding ? " \t" : "") + "\r\n").getBytes(US_ASCII);

    return bodyPart -> Concat.of(Source.of(wrap(b), wrap(headers(bodyPart))), bodyPart.body());
  }
}
