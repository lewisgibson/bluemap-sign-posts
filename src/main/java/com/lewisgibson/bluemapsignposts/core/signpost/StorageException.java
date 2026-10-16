package com.lewisgibson.bluemapsignposts.core.signpost;

/** The store could not be opened, read or written. The message says which and why. */
public final class StorageException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param message which operation failed and against what, for the operator reading the log
     * @param cause the underlying failure, or {@code null} when the store itself was the cause
     */
    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
