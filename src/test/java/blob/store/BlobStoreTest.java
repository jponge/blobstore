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

import com.google.common.io.ByteStreams;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;
import com.google.common.io.InputSupplier;
import org.jboss.byteman.contrib.bmunit.BMRule;
import org.jboss.byteman.contrib.bmunit.BMScript;
import org.jboss.byteman.contrib.bmunit.BMUnitRunner;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.Map;

import static com.google.common.base.Charsets.*;
import static com.google.common.io.CharStreams.newReaderSupplier;
import static com.google.common.io.Files.append;
import static com.google.common.io.Files.newInputStreamSupplier;
import static com.google.common.io.Files.newOutputStreamSupplier;
import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.matchers.JUnitMatchers.*;

@RunWith(BMUnitRunner.class)
public class BlobStoreTest {

    static final String BYTEMAN_SCRIPTS = "target/test-classes/byteman";
    static final String SAMPLE_SHA1 = "ae1a077157f51540d0b082689b91d7283d7170f5";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void create_in_empty_existing_dir() {
        BlobStore store = new BlobStore(temporaryFolder.getRoot());
        assertThat(store.getIndex().size(), is(0));
    }

    @Test
    public void create_in_nonexistent_dir() throws IOException {
        BlobStore store = new BlobStore(new File(temporaryFolder.getRoot(), "missing"));
        assertThat(store.getIndex().size(), is(0));
    }

    @Test(expected = BlobStoreException.class)
    public void create_over_existing_file() throws IOException {
        new BlobStore(temporaryFolder.newFile());
    }

    @Test
    public void create_in_existing_dir_with_fake_index() throws IOException {
        File indexFile = temporaryFolder.newFile("index");
        Files.append("foo.txt => 9b6a1b1da2cb1b2ab15681a7a0a75e6b4b812bb0\n",
                indexFile,
                UTF_8);
        Files.append("Plop da plop => 9b6a1b1da2cb1b2ab15681a7a0a75e6b4b812bb0\n",
                indexFile,
                UTF_8);

        BlobStore store = new BlobStore(temporaryFolder.getRoot());

        Map<String,String> index = store.getIndex();
        assertThat(index.size(), is(2));
        assertThat(index.keySet(), hasItem("foo.txt"));
        assertThat(index.keySet(), hasItem("Plop da plop"));
        assertThat(index.keySet(), not(hasItem("Plop da plop!")));
        assertThat(index.get("foo.txt"), is("9b6a1b1da2cb1b2ab15681a7a0a75e6b4b812bb0"));
        assertThat(index.get("Plop da plop"), is("9b6a1b1da2cb1b2ab15681a7a0a75e6b4b812bb0"));
    }

    @Test(expected = BlobStoreException.class)
    public void create_in_existing_dir_with_corrupt_index() throws IOException {
        File indexFile = temporaryFolder.newFile("index");
        Files.append("BOO!", indexFile, UTF_8);
        new BlobStore(temporaryFolder.getRoot());
    }
    
    @Test
    public void store_some_files() throws IOException {
        BlobStore store = new BlobStore(temporaryFolder.getRoot());

        store.put("FOO", newInputStreamSupplier(new File("pom.xml")));
        store.put("sample", newInputStreamSupplier(new File("src/test/resources/sample")));

        assertThat(store.getIndex().size(), is(2));
        assertThat(store.getIndex().get("sample"), is(SAMPLE_SHA1));
        assertThat(new File(temporaryFolder.getRoot(), SAMPLE_SHA1).exists(), is(true));
    }
    
    @Test
    public void verify_index_over_existing_store() throws IOException {
        store_some_files();        
        BlobStore store = new BlobStore(temporaryFolder.getRoot());

        assertThat(store.getIndex().size(), is(2));
        assertThat(store.getIndex().get("sample"), is(SAMPLE_SHA1));
    }

    @Test
    public void store_duplicate() throws IOException {
        store_some_files();
        BlobStore store = new BlobStore(temporaryFolder.getRoot());

        store.put("sample-bis", newInputStreamSupplier(new File("src/test/resources/sample")));
        assertThat(store.getIndex().size(), is(3));
        assertThat(store.getIndex().get("sample-bis"), is(SAMPLE_SHA1));
    }

    @Test
    public void verify_store_then_get() throws IOException {
        BlobStore store = new BlobStore(temporaryFolder.getRoot());
        
        File temp = File.createTempFile("store", "get");
        append("Hello world!", temp, UTF_8);
        store.put("hello.txt", newInputStreamSupplier(temp));

        ByteStreams.copy(store.get("hello.txt").get(), newOutputStreamSupplier(temp));
        String content = Files.readFirstLine(temp, UTF_8);
        
        assertThat(content, is("Hello world!"));
        assertThat(store.get("plop").orNull(), is(nullValue()));
    }
    
    @Test
    public void verify_blob_removal() throws IOException {
        BlobStore store = new BlobStore(temporaryFolder.getRoot());
        
        store.put("POM", newInputStreamSupplier(new File("pom.xml")));        
        assertThat(store.getIndex().size(), is(1));

        store.remove("POM");
        assertThat(store.getIndex().size(), is(0));
        
        store.remove("Hey!");
    }

    @Test
    public void verify_blob_external_removal() throws IOException {
        BlobStore store = new BlobStore(temporaryFolder.getRoot());

        store.put("sample", newInputStreamSupplier(new File("src/test/resources/sample")));
        assertThat(store.getIndex().size(), is(1));
        assertThat(store.get("sample").orNull(), is(notNullValue()));

        File blob = new File(temporaryFolder.getRoot(), SAMPLE_SHA1);
        if (!blob.delete()) {
            throw new RuntimeException(blob + " could not be deleted");
        }
        assertThat(store.get("sample").orNull(), is(nullValue()));
        assertThat(store.getIndex().size(), is(0));
    }

    // Tests with Byteman ........................................................................................... //

    @Test
    @BMRule(name = "Trace constructor of BlobStore",
            targetClass = "blob.store.BlobStore",
            targetMethod = "<init>",
            condition = "true",
            action = "traceln(\"byteman_check_traceln() worked\")"
    )
    public void byteman_check_traceln() {
        new BlobStore(temporaryFolder.getRoot());
    }

    @BMScript(value="create_but_fail_to_mkdir", dir= BYTEMAN_SCRIPTS)
    @Test(expected = BlobStoreException.class)
    public void create_but_fail_to_mkdir() throws IOException {
        new BlobStore(new File(temporaryFolder.getRoot(), "missing"));
    }

    @BMScript(value="fail_reading_index_with_io_error", dir= BYTEMAN_SCRIPTS)
    @Test(expected = BlobStoreException.class)
    public void fail_reading_index_with_io_error() throws IOException {
        create_in_existing_dir_with_fake_index();
        new BlobStore(temporaryFolder.getRoot());
    }

    @BMScript(value="fail_to_rename_a_freshly_compressed_unnamed_blob", dir= BYTEMAN_SCRIPTS)
    @Test(expected = BlobStoreException.class)
    public void fail_to_rename_a_freshly_compressed_unnamed_blob() throws IOException {
        BlobStore store = new BlobStore(temporaryFolder.getRoot());
        store.put("sample", newInputStreamSupplier(new File("src/test/resources/sample")));
    }

    @BMScript(value="fail_to_compress_and_hash_blob_with_io_error", dir= BYTEMAN_SCRIPTS)
    @Test
    public void fail_to_compress_and_hash_blob_with_io_error() throws IOException {
        BlobStore store = new BlobStore(temporaryFolder.getRoot());
        try {
            store.put("sample", newInputStreamSupplier(new File("src/test/resources/sample")));
            fail("A BlobStoreException should have been throw");
        } catch (BlobStoreException ignored) {
            File temp = new File(temporaryFolder.getRoot(), "TEMP");
            File blob = new File(temporaryFolder.getRoot(), SAMPLE_SHA1);
            assertThat(temp.exists(), is(false));
            assertThat(blob.exists(), is(false));
        }
    }

    @BMScript(value="fail_to_append_in_index", dir= BYTEMAN_SCRIPTS)
    @Test
    public void fail_to_append_in_index() throws IOException {
        BlobStore store = new BlobStore(temporaryFolder.getRoot());
        try {
            store.put("sample", newInputStreamSupplier(new File("src/test/resources/sample")));
            fail("A BlobStoreException should have been throw");
        } catch (BlobStoreException ignored) {
            File temp = new File(temporaryFolder.getRoot(), "TEMP");
            File blob = new File(temporaryFolder.getRoot(), SAMPLE_SHA1);
            assertThat(temp.exists(), is(false));
            assertThat(blob.exists(), is(false));
        }
    }

    @BMScript(value="fail_with_io_error_on_get", dir= BYTEMAN_SCRIPTS)
    @Test(expected = BlobStoreException.class)
    public void fail_with_io_error_on_get() throws IOException {
        BlobStore store = new BlobStore(temporaryFolder.getRoot());
        store.put("sample", newInputStreamSupplier(new File("src/test/resources/sample")));
        store.get("sample");
    }

    @BMScript(value="fail_to_delete_index", dir= BYTEMAN_SCRIPTS)
    @Test(expected = BlobStoreException.class)
    public void fail_to_delete_index() throws IOException {
        BlobStore store = new BlobStore(temporaryFolder.getRoot());
        store.put("POM", newInputStreamSupplier(new File("pom.xml")));
        store.remove("POM");
    }

    @BMScript(value="fail_to_write_entries_in_index", dir= BYTEMAN_SCRIPTS)
    @Test(expected = BlobStoreException.class)
    public void fail_to_write_entries_in_index() throws IOException {
        BlobStore store = new BlobStore(temporaryFolder.getRoot());
        store.put("POM", newInputStreamSupplier(new File("pom.xml")));
        store.put("PAM", newInputStreamSupplier(new File("pom.xml")));
        store.put("PIM", newInputStreamSupplier(new File("pom.xml")));
        store.put("OMG", newInputStreamSupplier(new File("pom.xml")));
        store.remove("OMG");
    }

    @BMScript(value="fail_to_delete_a_blob_file_on_remove", dir= BYTEMAN_SCRIPTS)
    @Test(expected = BlobStoreException.class)
    public void fail_to_delete_a_blob_file_on_remove() throws IOException {
        BlobStore store = new BlobStore(temporaryFolder.getRoot());
        store.put("OMG", newInputStreamSupplier(new File("pom.xml")));
        store.remove("OMG");
    }

    @BMScript(value="simulate_vanished_blob_file_on_remove", dir= BYTEMAN_SCRIPTS)
    @Test
    public void simulate_vanished_blob_file_on_remove() throws IOException {
        BlobStore store = new BlobStore(temporaryFolder.getRoot());
        store.put("OMG", newInputStreamSupplier(new File("pom.xml")));
        store.remove("OMG");
    }
}
