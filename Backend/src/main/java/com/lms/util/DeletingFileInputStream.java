package com.lms.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A FileInputStream that automatically deletes its underlying File when closed.
 */
public class DeletingFileInputStream extends FileInputStream {
    private static final Logger LOG = LoggerFactory.getLogger(DeletingFileInputStream.class);
    private final File file;

    public DeletingFileInputStream(File file) throws FileNotFoundException {
        super(file);
        this.file = file;
    }

    @Override
    public void close() throws IOException {
        try {
            super.close();
        } finally {
            if (file != null && file.exists()) {
                boolean deleted = file.delete();
                if (deleted) {
                    LOG.info("Successfully deleted temporary file: {}", file.getAbsolutePath());
                } else {
                    LOG.warn("Failed to delete temporary file: {}", file.getAbsolutePath());
                }
            }
        }
    }
}
