package net.pincette.rs.multipart;

import static net.pincette.rs.Chain.with;
import static net.pincette.rs.Probe.probeMore;
import static net.pincette.rs.Probe.probeValue;
import static net.pincette.rs.Util.onCancelProcessor;
import static net.pincette.rs.Util.onCompleteProcessor;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.function.Function;
import net.pincette.rs.ProcessorBase;

/**
 * Takes a byte stream that starts with the MIME multipart preamble and converts it in a stream of
 * MIME body parts. It also consumes the epilogue.
 *
 * @author Werner Donné
 */
public class MultipartDecoder extends ProcessorBase<ByteBuffer, BodyPart> {
  private final StateMachine stateMachine;
  private BodyRecorder currentBodyRecorder;
  private HeadersRecorder currentHeadersRecorder;
  private boolean bodyComplete = true;
  private long bodyRequested;
  private boolean complete;
  private Map<String, String[]> pendingHeaders;
  private long requested;

  public MultipartDecoder(final String boundary) {
    this.stateMachine = new StateMachine(recorders(boundary), this::more);
  }

  private BodyRecorder bodyRecorder() {
    if (!ongoingBody()) {
      currentBodyRecorder = new BodyRecorder();
    }

    return currentBodyRecorder;
  }

  private void completeBody() {
    dispatch(
        () -> {
          bodyComplete = true;

          if (complete) {
            subscriber.onComplete();
          } else {
            if (pendingHeaders != null) {
              flushPendingHeaders();
            } else {
              stateMachine.resume();
            }
          }
        });
  }

  @Override
  protected void emit(final long number) {
    dispatch(
        () -> {
          requested += number;

          if (pendingHeaders != null) {
            flushPendingHeaders();
          } else {
            more();
          }
        });
  }

  private void emitBodyPart(final Map<String, String[]> headers) {
    bodyComplete = false;
    --requested;

    subscriber.onNext(
        new BodyPart(
            headers,
            with(currentBodyRecorder.publisher())
                .map(onCompleteProcessor(this::completeBody))
                .map(onCancelProcessor(this::completeBody))
                .map(
                    probeMore(
                        n -> {
                          bodyRequested += n;
                          stateMachine.resume();
                        }))
                .map(
                    probeValue(
                        v -> {
                          --bodyRequested;

                          if (bodyRequested == 0) {
                            stateMachine.suspend();
                          }
                        }))
                .get()));

    currentHeadersRecorder = null;
  }

  private void flushPendingHeaders() {
    final Map<String, String[]> headers = pendingHeaders;

    pendingHeaders = null;
    emitBodyPart(headers);
  }

  private HeadersRecorder headersRecorder() {
    if (currentHeadersRecorder == null || currentHeadersRecorder.isComplete()) {
      currentHeadersRecorder = new HeadersRecorder(this::onHeadersCommit);
    }

    return currentHeadersRecorder;
  }

  private void more() {
    dispatch(() -> subscription.request(1));
  }

  @Override
  public void onComplete() {
    dispatch(
        () -> {
          complete = true;

          if (bodyComplete) {
            subscriber.onComplete();
          }

          stateMachine.complete();
        });
  }

  private void onHeadersCommit(final Map<String, String[]> headers) {
    dispatch(
        () -> {
          stateMachine.suspend();

          if (requested > 0 && bodyComplete) {
            emitBodyPart(headers);
          } else {
            pendingHeaders = headers;
          }
        });
  }

  @Override
  public void onNext(final ByteBuffer buffer) {
    stateMachine.next(buffer);
  }

  private boolean ongoingBody() {
    return currentBodyRecorder != null && !currentBodyRecorder.isComplete();
  }

  private Function<States, Recorder> recorders(final String boundary) {
    return state ->
        switch (state) {
          case BODY -> bodyRecorder();
          case DELIMITER -> new DelimiterRecorder(boundary);
          case HEADERS -> headersRecorder();
          default -> new DiscardRecorder();
        };
  }
}
