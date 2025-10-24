package uk.gov.dbt.ndtp.federator.processor.file.fetcher.impl;

import java.io.InputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import uk.gov.dbt.ndtp.federator.exceptions.FileFetcherException;
import uk.gov.dbt.ndtp.federator.model.FileFetchRequest;
import uk.gov.dbt.ndtp.federator.processor.file.fetcher.FileFetchResult;
import uk.gov.dbt.ndtp.federator.processor.file.fetcher.FileFetcher;

public class S3FileFetcher implements FileFetcher {

    private final S3Client s3Client;

    public S3FileFetcher(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    @Override
    public FileFetchResult fetch(FileFetchRequest request) {
        try {
            long size = s3Client.headObject(HeadObjectRequest.builder()
                            .bucket(request.bucketOrContainer())
                            .key(request.path())
                            .build())
                    .contentLength();

            InputStream stream = s3Client.getObject(GetObjectRequest.builder()
                    .bucket(request.bucketOrContainer())
                    .key(request.path())
                    .build());

            return new FileFetchResult(stream, size);

        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                throw new FileFetcherException(
                        "File not found in S3: " + request.bucketOrContainer() + "/" + request.path());
            }
            throw new FileFetcherException(
                    "S3 error fetching: " + request.bucketOrContainer() + "/" + request.path(), e);
        } catch (Exception e) {
            throw new FileFetcherException(
                    "Failed to fetch from S3: " + request.bucketOrContainer() + "/" + request.path(), e);
        }
    }
}
