package net.pincette.rs.multipart;

import java.nio.ByteBuffer;

/**
 * @author Werner Donné
 */
interface Recorder {
  void bufferDepleted();

  void commit();

  boolean isCloseDelimiter();

  boolean isComplete();

  boolean next(byte b);

  ByteBuffer rollback();
}
