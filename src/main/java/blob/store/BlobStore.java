/*
 * Copyright (c) 2012 Julien Ponge.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package blob.store;

import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.common.io.*;

import java.io.*;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.collect.Maps.newHashMap;
import static com.google.common.hash.Hashing.sha1;
import static com.google.common.io.ByteStreams.*;
import static com.google.common.io.Files.append;
import static com.google.common.io.Files.readLines;
import static java.util.Collections.shuffle;
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

    private void ensureValidWorkingDirectory(File workingDirectory) {
        if (workingDirectory.isFile()) {
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

    public Map<String, String> getIndex() {
        return unmodifiableMap(index);
    }

    public void put(String key, InputSupplier<? extends InputStream> supplier) {
        File tempFile = new File(workingDirectory, "TEMP");
        File blobFile = null;

        try {
            FileOutputStream fileOutputStream = new FileOutputStream(tempFile);
            final GZIPOutputStream gzipOutputStream = new GZIPOutputStream(fileOutputStream);

            ByteProcessor<String> processor = new ByteProcessor<String>() {
                Hasher hasher = sha1().newHasher();

                @Override
                public boolean processBytes(byte[] bytes, int offset, int length) throws IOException {
                    hasher.putBytes(bytes, offset, length);
                    gzipOutputStream.write(bytes, offset, length);
                    return true;
                }

                @Override
                public String getResult() {
                    return hasher.hash().toString();
                }
            };

            // Compress the blob files and compute the SHA1
            String sha1 = readBytes(supplier, processor);
            gzipOutputStream.close();
            blobFile = new File(workingDirectory, sha1);
            if (!blobFile.exists()) {
                if (!tempFile.renameTo(blobFile)) {
                    throw new BlobStoreException("Could not rename " + tempFile + " to " + sha1);
                }
            }

            // Update the index
            index.put(key, sha1);
            append(indexLineFor(key, sha1), indexFile, UTF_8);

        } catch (IOException e) {
            // Do our best to clean up the files, but do not check the return values
            if (tempFile.exists()) {
                tempFile.delete();
            }
            if (blobFile != null && blobFile.exists()) {
                blobFile.delete();
            }
            throw new BlobStoreException(e);
        }
    }

    private String indexLineFor(String key, String sha1) {
        return key + INDEX_LINE_SEPARATOR + sha1 + "\n";
    }

    public Optional<InputStream> get(String key) {
        if (index.containsKey(key)) {
            try {
                InputStream in = openBlobStream(key);
                return Optional.of(in);
            } catch (FileNotFoundException e) {
                removeInvalidKeyAndRewriteIndex(key);
                return Optional.absent();
            } catch (IOException e) {
                removeInvalidKeyAndRewriteIndex(key);
                throw new BlobStoreException(e);
            }
        }
        return Optional.absent();
    }

    private GZIPInputStream openBlobStream(String key) throws IOException {
        return new GZIPInputStream(new FileInputStream(new File(workingDirectory, index.get(key))));
    }

    private void removeInvalidKeyAndRewriteIndex(String key) {
        index.remove(key);
        rewriteIndex();
    }

    private void rewriteIndex() {
        if (!indexFile.delete()) {
            throw new BlobStoreException("Could not delete " + indexFile);
        }
        try {
            for (String key : index.keySet()) {
                append(indexLineFor(key, index.get(key)), indexFile, UTF_8);
            }
        } catch (IOException e) {
            // This implementation has a vulnerability window for loosing the index!
            throw new BlobStoreException(e);
        }
    }

    public void remove(String key) {
        if (index.containsKey(key)) {
            String sha1 = index.get(key);
            index.remove(key);
            File blob = new File(workingDirectory, sha1);
            if (blob.exists()) {
                if (!blob.delete()) {
                    throw new BlobStoreException("Could not delete " + blob);
                }
            }
            rewriteIndex();
        }
    }
}
