package com.memoirist.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import com.memoirist.common.StorageUnavailableException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * What the listener does with a storage failure. The transaction side (commit deletes, rollback keeps) needs a real
 * transaction and is in {@code AfterCommitDeletionTest}.
 */
@ExtendWith(OutputCaptureExtension.class)
class ObjectDeletionListenerTest {

    private final StorageService storage = mock(StorageService.class);
    private final ObjectDeletionListener listener = new ObjectDeletionListener(storage);

    @Test
    void deletesEveryKeyInOrder() {
        listener.deleteObjects(new ObjectDeletionRequested(List.of("a", "b", "c")));

        InOrder inOrder = inOrder(storage);
        inOrder.verify(storage).delete("a");
        inOrder.verify(storage).delete("b");
        inOrder.verify(storage).delete("c");
    }

    @Test
    void aFailingKeyIsLoggedAndTheOthersAreStillDeleted(CapturedOutput output) {
        S3Exception storageDown = (S3Exception) S3Exception.builder()
                // What a real SDK message can hold: the endpoint and the bucket, and for some calls a signed URL.
                .message("Connect to http://secret-host.internal:8333/memoirist-media/b?X-Amz-Signature=abc123 failed")
                .statusCode(503)
                .build();
        doThrow(new StorageUnavailableException("Deleting an object failed", storageDown))
                .when(storage)
                .delete("b");

        // The transaction has already committed. An exception here would turn a saved change into a failed request.
        assertThatCode(() -> listener.deleteObjects(new ObjectDeletionRequested(List.of("a", "b", "c"))))
                .doesNotThrowAnyException();

        InOrder inOrder = inOrder(storage);
        inOrder.verify(storage).delete("a");
        inOrder.verify(storage).delete("b");
        inOrder.verify(storage).delete("c");
        assertThat(output.getOut() + output.getErr())
                .contains("Could not delete stored object b")
                .contains("software.amazon.awssdk.services.s3.model.S3Exception, HTTP 503")
                // The log says which key and what kind of failure, never the SDK's message (security.md).
                .doesNotContain("secret-host")
                .doesNotContain("X-Amz-Signature")
                .doesNotContain("abc123");
    }

    @Test
    void anUnexpectedRuntimeExceptionIsAlsoContained() {
        doThrow(new IllegalStateException("boom")).when(storage).delete("a");

        assertThatCode(() -> listener.deleteObjects(new ObjectDeletionRequested(List.of("a", "b"))))
                .doesNotThrowAnyException();

        InOrder inOrder = inOrder(storage);
        inOrder.verify(storage).delete("a");
        inOrder.verify(storage).delete("b");
    }
}
