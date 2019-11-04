package com.android.wallpaper.util;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

/**
 * Common utility methods for moving app files around.
 */
public class FileMover {

    /**
     * Moves a file from the {@code srcContext}'s files directory to the files directory for the
     * given {@code dstContext}.
     * @param srcContext {@link Context} used to open the file corresponding to srcFileName
     * @param srcFileName Name of the source file (just the name, no path). It's expected to be
     *                    located in {@link Context#getFilesDir()} for {@code srcContext}
     * @param dstContext {@link Context} used to open the file corresponding to dstFileName
     * @param dstFileName Name of the destination file (just the name, no path), which will be
     *                    located in {@link Context#getFilesDir()} for {@code dstContext}
     * @return a {@link File} corresponding to the moved file in its new location, or null if
     *      nothing was moved (because srcFileName didn't exist).
     */
    public static File moveFileBetweenContexts(Context srcContext, String srcFileName,
                                               Context dstContext, String dstFileName)
            throws IOException {
        File srcFile = srcContext.getFileStreamPath(srcFileName);
        if (srcFile.exists()) {
            try (FileInputStream input = srcContext.openFileInput(srcFileName);
                 FileOutputStream output = dstContext.openFileOutput(
                         dstFileName, Context.MODE_PRIVATE)) {
                FileChannel inputChannel = input.getChannel();
                inputChannel.transferTo(0, inputChannel.size(), output.getChannel());
                output.flush();
                srcContext.deleteFile(srcFileName);
            }
            return dstContext.getFileStreamPath(dstFileName);
        }
        return null;
    }
}
