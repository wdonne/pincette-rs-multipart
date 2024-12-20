package net.pincette.rs.multipart;

import static java.util.Arrays.stream;
import static net.pincette.rs.multipart.States.BODY;
import static net.pincette.rs.multipart.States.CLOSE;
import static net.pincette.rs.multipart.States.DELIMITER;
import static net.pincette.rs.multipart.States.EPILOGUE;
import static net.pincette.rs.multipart.States.HEADERS;
import static net.pincette.rs.multipart.States.PREAMBLE;

import java.nio.ByteBuffer;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * @author Werner Donné
 */
class StateMachine {
  private static final StateTransition[] TRANSITIONS = {
    new StateTransition(PREAMBLE, DELIMITER, (r, b) -> b == (byte) '\r'),
    new StateTransition(DELIMITER, HEADERS, (r, b) -> r.isComplete()),
    new StateTransition(HEADERS, BODY, (r, b) -> r.isComplete()),
    new StateTransition(BODY, DELIMITER, (r, b) -> b == (byte) '\r'),
    new StateTransition(CLOSE, EPILOGUE, (r, b) -> r.isComplete())
  };

  private ByteBuffer buffer;
  private final Function<States, Recorder> getRecorder;
  private final Runnable more;
  private Recorder previousRecorder;
  private States previousState;
  private Recorder recorder;
  private ByteBuffer rolledback;
  private States state = PREAMBLE;
  private boolean suspended;

  StateMachine(final Function<States, Recorder> getRecorder, final Runnable more) {
    this.getRecorder = getRecorder;
    this.more = more;
    recorder = this.getRecorder.apply(state);
  }

  void complete() {
    wrapUpRecorder();
  }

  private void consume() {
    while (hasEnough()) {
      final byte b = getByte();

      if (rolledback == null || !rolledback.hasRemaining()) {
        // The last one may be the start of a new state.
        newState(b);
      }

      if (!recorder.next(b) && !recorder.isComplete()) {
        rollback();
      }
    }

    if (!suspended) {
      more.run();
    }
  }

  private byte getByte() {
    if (rolledback != null && rolledback.hasRemaining()) {
      return rolledback.get();
    }

    rolledback = null;

    return buffer.get();
  }

  private void goToNextState(final StateTransition transition) {
    wrapUpRecorder();
    state = transition.to;
    recorder = getRecorder.apply(state);
  }

  boolean hasEnough() {
    return buffer != null && buffer.hasRemaining();
  }

  void next(final ByteBuffer buffer) {
    if (hasEnough()) {
      throw new IllegalStateException("State machine is full");
    }

    this.buffer = buffer;
    consume();
  }

  private void newState(final byte b) {
    if (state == DELIMITER && recorder.isCloseDelimiter()) {
      state = CLOSE;
    }

    stream(TRANSITIONS)
        .filter(t -> t.signal.test(recorder, b) && t.from == state && t.from != t.to)
        .findFirst()
        .ifPresent(this::goToNextState);
  }

  private void rollback() {
    if (previousState == null || previousRecorder == null) {
      throw new IllegalStateException("State machine cannot go back to previous state");
    }

    rolledback = recorder.rollback();
    state = previousState;
    previousState = null;
    recorder = previousRecorder;
    previousRecorder = null;
  }

  void resume() {
    if (suspended) {
      more.run();
    }

    suspended = false;
  }

  void suspend() {
    suspended = true;
  }

  private void wrapUpRecorder() {
    if (recorder.isComplete()) {
      if (previousRecorder != null) {
        previousRecorder.commit();
        previousRecorder = null;
        previousState = null;
      }

      recorder.commit();
    } else {
      previousRecorder = recorder;
      previousState = state;
    }
  }

  private record StateTransition(States from, States to, BiPredicate<Recorder, Byte> signal) {}
}
