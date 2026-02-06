package net.pincette.rs.multipart;

import static java.nio.ByteBuffer.allocate;
import static net.pincette.rs.QueuePublisher.queuePublisher;

import java.nio.ByteBuffer;
import java.util.concurrent.Flow.Publisher;
import net.pincette.rs.QueuePublisher;

/**
 * @author Werner Donné
 */
class BodyRecorder implements Recorder {
  private final QueuePublisher<ByteBuffer> publisher = queuePublisher();
  private ByteBuffer buffer;
  private boolean complete;

  public void bufferDepleted() {
    newBuffer();
  }

  private boolean canWrite() {
    return buffer != null && buffer.remaining() > 0;
  }

  public void commit() {
    complete = true;
    publishBuffer();
    publisher.close();
  }

  public boolean isCloseDelimiter() {
    return false;
  }

  public boolean isComplete() {
    return complete;
  }

  public boolean next(final byte b) {
    if (!canWrite()) {
      newBuffer();
    }

    buffer.put(b);

    return true;
  }

  private void newBuffer() {
    publishBuffer();
    buffer = allocate(0xffff);
  }

  private void publishBuffer() {
    if (buffer != null) {
      buffer.limit(buffer.position());
      buffer.position(0);
      publisher.getQueue().add(buffer);
      buffer = null;
    }
  }

  Publisher<ByteBuffer> publisher() {
    return publisher;
  }

  public ByteBuffer rollback() {
    return null;
  }
}
