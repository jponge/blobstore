package blob.store;

import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.collect.Maps;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.collect.Maps.newHashMap;
import static com.google.common.io.Files.readLines;
import static java.util.Collections.enumeration;
import static java.util.Collections.unmodifiableMap;

public class BlobStore {

    private static final String INDEX_FILENAME = "index";
    private static final String INDEX_LINE_SEPARATOR = " => ";

    private final File workingDirectory;
    private final File indexFile;
    private final Map<String, String> index = newHashMap();

    public BlobStore(File workingDirectory) {
        ensureValidWorkingDirectory(workingDirectory);
        this.workingDirectory = workingDirectory;

        indexFile = new File(workingDirectory, INDEX_FILENAME);
        if (indexFile.exists()) {
            populateIndex();
        }
    }

    public Map<String, String> getIndex() {
        return unmodifiableMap(index);
    }

    private void ensureValidWorkingDirectory(File workingDirectory) {
        if (workingDirectory.exists() && workingDirectory.isFile()) {
            throw new BlobStoreException(workingDirectory.getAbsolutePath() + " already exists and is a file");
        } else if (!workingDirectory.exists()) {
            if (!workingDirectory.mkdirs()) {
                throw new BlobStoreException("Could not mkdir " + workingDirectory.getAbsolutePath());
            }
        }
    }

    private void populateIndex() {
        try {
            Splitter splitter = Splitter.on(INDEX_LINE_SEPARATOR);
            for (String line : readLines(indexFile, UTF_8)) {
                Iterator<String> iterator = splitter.split(line).iterator();
                index.put(iterator.next(), iterator.next());
            }
        } catch (IOException e) {
            throw new BlobStoreException("Error while reading from the index file", e);
        } catch (NoSuchElementException e) {
            throw new BlobStoreException("Corrupt index file", e);
        }
    }
}
