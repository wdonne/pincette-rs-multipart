package net.pincette.rs.multipart;

import java.nio.ByteBuffer;

class DiscardRecorder implements Recorder {
  public void bufferDepleted() {
    // Not interested.
  }

  public void commit() {
    // Nothing to do.
  }

  public boolean isCloseDelimiter() {
    return false;
  }

  public boolean isComplete() {
    return false;
  }

  public boolean next(final byte b) {
    return true;
  }

  public ByteBuffer rollback() {
    return null;
  }
}
