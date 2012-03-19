package blob.store.BlobStore;

import java.io.File;

public class BlobStore {

    private final File workingDirectory;

    public BlobStore(File workingDirectory) {
        this.workingDirectory = workingDirectory;
        if (workingDirectory.exists() && workingDirectory.isFile()) {
            throw new BlobStoreException(workingDirectory.getAbsolutePath() + " already exists and is a file");
        } else if (!workingDirectory.exists()) {
            if (!workingDirectory.mkdirs()) {
                throw new BlobStoreException("Could not mkdir " + workingDirectory.getAbsolutePath());
            }
        }
    }


}
