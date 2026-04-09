package com.travel_ease.hotel_system.service.impl;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.travel_ease.hotel_system.service.FileService;
import com.travel_ease.hotel_system.util.CommonFileSavedBinaryDataDTO;
import com.travel_ease.hotel_system.util.ImageUploadGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import javax.sql.rowset.serial.SerialBlob;
import java.io.IOException;
import java.sql.SQLException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private final AmazonS3 s3;
    private final ImageUploadGenerator imageUploadGenerator;

    @Override
    public CommonFileSavedBinaryDataDTO createResource(MultipartFile file,
                                                       String directory,
                                                       String bucket) {
        try {
            // 1️⃣ Generate unique file name
            String originalFilename = file.getOriginalFilename();
            String newFileName = imageUploadGenerator.generateCPDResourceName(
                    originalFilename, UUID.randomUUID().toString());

            // 2️⃣ Set metadata
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            metadata.setContentType(file.getContentType());

            // 3️⃣ Full path (directory + file)
            String key = directory + "/" + newFileName;

            // 4️⃣ Create S3 Put request (NO ACL, bucket owner enforced)
            PutObjectRequest request = new PutObjectRequest(
                    bucket,
                    key,
                    file.getInputStream(),
                    metadata
            );

            // 5️⃣ Upload file to S3
            PutObjectResult result = s3.putObject(request);

            // 6️⃣ Get file URL
            String fileUrl = s3.getUrl(bucket, key).toString();

            // 7️⃣ Return DTO
            return new CommonFileSavedBinaryDataDTO(
                    new SerialBlob(result.getContentMd5().getBytes()),
                    directory,
                    new SerialBlob(newFileName.getBytes()),
                    new SerialBlob(fileUrl.getBytes())
            );

        } catch (SQLException | IOException e) {
            throw new RuntimeException("Failed to upload file to S3", e);
        }
    }

    @Override
    public void deleteResource(String bucket, String directory, String fileName) {
        String key = directory + "/" + fileName;
        s3.deleteObject(bucket, key);
    }

    @Override
    public byte[] downloadFile(String bucket, String fileName) {
        var object = s3.getObject(bucket, fileName);
        try (var objectContent = object.getObjectContent()) {
            return objectContent.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException("Failed to download file from S3", e);
        }
    }
}
