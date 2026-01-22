package com.ecat.core.Utils.NestedJarLoader;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.net.JarURLConnection;
import java.util.jar.JarFile;
import java.util.jar.JarEntry;

/**
 * 处理JAR URL连接的处理器
 */
public class JarURLConnectionHandler extends URLStreamHandler {

    private final NestedJarClassLoader classLoader;

    public JarURLConnectionHandler(NestedJarClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Override
    protected URLConnection openConnection(URL url) throws IOException {
        return new NestedJarURLConnection(url);
    }

    private class NestedJarURLConnection extends JarURLConnection {
        private Archive archive;
        private Archive.ArchiveEntry entry;
        private boolean connected = false;

        protected NestedJarURLConnection(URL url) throws IOException {
            super(url);
        }

        @Override
        public void connect() throws IOException {
            if (!connected) {
                String spec = getURL().getFile();
                int separatorIndex = spec.indexOf('!');
                if (separatorIndex == -1) {
                    throw new IOException("Invalid JAR URL: " + getURL());
                }
                String archiveUrl = spec.substring(0, separatorIndex);
                String entryName = spec.substring(separatorIndex + 1);
                if (entryName.startsWith("/")) {
                    entryName = entryName.substring(1);
                }
                for (Archive a : classLoader.archives) {
                    if (a.getUrl().toString().equals(archiveUrl)) {
                        this.archive = a;
                        break;
                    }
                }
                if (this.archive == null) {
                    throw new IOException("Archive not found: " + archiveUrl);
                }
                this.entry = this.archive.getEntry(entryName);
                this.connected = true;
            }
        }

        @Override
        public InputStream getInputStream() throws IOException {
            connect();
            if (entry == null) {
                throw new IOException("Entry not found: " + getURL().getFile());
            }
            return archive.getInputStream(entry);
        }

        @Override
        public int getContentLength() {
            try {
                connect();
                return entry != null ? (int) entry.getSize() : -1;
            } catch (IOException e) {
                return -1;
            }
        }

        @Override
        public JarFile getJarFile() throws IOException {
            connect();
            if (archive instanceof JarFileArchive) {
                return ((JarFileArchive) archive).jarFile;
            }
            throw new IOException("Not a JarFileArchive");
        }

        @Override
        public JarEntry getJarEntry() throws IOException {
            connect();
            if (entry instanceof JarFileArchive.JarFileArchiveEntry) {
                return ((JarFileArchive.JarFileArchiveEntry) entry).getJarEntry();
            }
            return null;
        }
    }
}
