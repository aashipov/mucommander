/**
 * This file is part of muCommander, http://www.mucommander.com
 * <p>
 * muCommander is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * muCommander is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * <p>
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.mucommander.commons.file.protocol.gcs;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import com.mucommander.commons.file.FileURL;
import com.mucommander.commons.util.StringUtils;

import java.io.IOException;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Representation of the Bucket as a Folder for the CloudStorage. The bucket lists its content as its children.
 *
 * @author miroslav.spak
 */
public class GoogleCloudStorageBucket extends GoogleCloudStorageAbstractFile {

    static final String GCS_BUCKET_LOCATION = "gcs_bucket_location";

    private Bucket bucket;

    GoogleCloudStorageBucket(FileURL url) {
        super(url);
    }

    GoogleCloudStorageBucket(FileURL url, Bucket bucket) {
        super(url);
        this.bucket = bucket;
    }

    /**
     * Reads bucket name from the standard {@link FileURL} representation. I.e., first level after the root separator.
     */
    protected String getBucketName() {
        // Find the first part of the path that represents bucket name
        return fileURL.getPath().replaceAll("/([^/]+)/?.*", "$1");
    }

    /**
     * Tries to receive bucket from the Google Cloud Storage service.
     *
     * @return bucket for this object path (i.e., {@link GoogleCloudStorageBucket#fileURL}), can be <b>null</b>
     * if bucket doesn't exist
     */
    protected Bucket getBucket() {
        if (bucket == null) {
            try {
                bucket = getStorageService().get(getBucketName());
            } catch (IOException ex) {
                // We were unable to receive bucket, try to continue work without it
                return null;
            }
        }

        // Bucket can be null if it doesn't exist
        return bucket;
    }

    @Override
    public boolean isDirectory() {
        // Bucket is always represented as directory
        return true;
    }

    @Override
    public boolean exists() {
        return getBucket() != null && getBucket().exists();
    }

    @Override
    protected Stream<GoogleCloudStorageAbstractFile> listDir() {
        if (getBucket() == null) {
            throw new IllegalStateException("Cannot list bucket that doesn't exist, bucket path " + getURL());
        }
        var files = getBucket().list(Storage.BlobListOption.currentDirectory());
        return StreamSupport.stream(files.iterateAll().spliterator(), false)
                .map(this::toFile);
    }

    /**
     * Transforms given Cloud Storage Blob to the {@link GoogleCloudStorageFile}.
     */
    protected GoogleCloudStorageFile toFile(Blob blob) {
        return toFile(
                parentPath -> parentPath + blob.getName(),
                url -> new GoogleCloudStorageFile(url, getBucket(), blob));
    }

    @Override
    public long getDate() {
        if (getBucket() == null) {
            // Unknown date for the missing Bucket
            return 0;
        }

        return getBucket().getUpdateTimeOffsetDateTime().toInstant().toEpochMilli();
    }

    @Override
    public void mkdir() throws IOException {
        var location = getURL().getProperty(GCS_BUCKET_LOCATION);

        try {
            var bucketBuilder = BucketInfo.newBuilder(getBucketName());
            if (!StringUtils.isNullOrEmpty(location)) {
                // Set location only if provided
                bucketBuilder.setLocation(location);
            }
            bucket = getStorageService().create(bucketBuilder.build());
        } catch (Exception ex) {
            throw new IOException("Unable to create bucket " + getBucketName(), ex);
        }
    }

    @Override
    public void delete() throws IOException {
        try {
            if (getBucket() != null && getBucket().delete()) {
                // The bucket was deleted
                bucket = null;
            } else {
                throw new IllegalStateException("Bucket " + getBucketName() + " wasn't deleted, it's probably missing");
            }
        } catch (Exception ex) {
            throw new IOException("Unable to delete bucket " + getBucketName(), ex);
        }
    }
}
