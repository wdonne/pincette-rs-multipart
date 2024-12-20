package net.pincette.rs.multipart;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.Flow.Publisher;

/**
 * Represents a MIME multipart body part.
 *
 * @param headers the headers of the body part.
 * @param body the body of the body part as a Reactive Streams publisher.
 * @author Werner Donné
 */
public record BodyPart(Map<String, String[]> headers, Publisher<ByteBuffer> body) {}
