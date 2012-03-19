package blob.store;

public class BlobStoreException extends RuntimeException {

    public BlobStoreException() {
        super();
    }

    public BlobStoreException(String s) {
        super(s);
    }

    public BlobStoreException(String s, Throwable throwable) {
        super(s, throwable);
    }

    public BlobStoreException(Throwable throwable) {
        super(throwable);
    }
}
