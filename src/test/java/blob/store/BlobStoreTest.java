package blob.store;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import static com.google.common.base.Charsets.*;
import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.matchers.JUnitMatchers.*;

public class BlobStoreTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void create_in_empty_existing_dir() {
        BlobStore store = new BlobStore(temporaryFolder.getRoot());
        assertThat(store.getIndex().size(), is(0));
    }

    @Test
    public void create_in_nonexistent_dir() throws IOException {
        BlobStore store = new BlobStore(temporaryFolder.newFolder());
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
}