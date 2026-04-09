package com.travel_ease.hotel_system.service.impl;

import com.amazonaws.services.accessanalyzer.model.InternalServerException;
import com.travel_ease.hotel_system.entity.User;
import com.travel_ease.hotel_system.entity.UserAvatar;
import com.travel_ease.hotel_system.exception.EntryNotFoundException;
import com.travel_ease.hotel_system.repository.UserAvatarRepository;
import com.travel_ease.hotel_system.repository.UserRepository;
import com.travel_ease.hotel_system.service.FileService;
import com.travel_ease.hotel_system.service.UserAvatarService;
import com.travel_ease.hotel_system.util.CommonFileSavedBinaryDataDTO;
import com.travel_ease.hotel_system.util.FileDataExtractor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserAvatarServiceImpl implements UserAvatarService {

    @Value("${bucketName}")
    private String bucketName;

    private final UserAvatarRepository userAvatarRepository;
    private final UserRepository userRepository;
    private final FileService fileService;
    private final FileDataExtractor fileDataExtractor;

    @Override
    public void createSystemUserAvatar(MultipartFile file, String email) throws SQLException {
        Optional<User> selectedUserOpt = userRepository.findByEmail(email);
        if (selectedUserOpt.isEmpty()) {
            throw new EntryNotFoundException("User not found");
        }
        User selectedUser = selectedUserOpt.get();

        String directory = "avatar/" + selectedUser.getId() + "/resource/";
        CommonFileSavedBinaryDataDTO resource = null;

        Optional<UserAvatar> selectedAvatarOpt = userAvatarRepository.findById(selectedUser.getId());

        try {
            // If avatar exists, delete old file
            if (selectedAvatarOpt.isPresent()) {
                UserAvatar selectedAvatar = selectedAvatarOpt.get();
                try {
                    String oldFileName = fileDataExtractor.byteArrayToString(selectedAvatar.getFileName());
                    fileService.deleteResource(bucketName, directory, oldFileName);
                } catch (Exception e) {
                    throw new InternalServerException("Failed to delete existing avatar resource");
                }
            }

            // Upload new avatar
            resource = fileService.createResource(file, directory, bucketName);
            if (resource == null) {
                throw new InternalServerException("Avatar upload failed");
            }

            if (selectedAvatarOpt.isPresent()) {
                // Update existing avatar
                UserAvatar selectedAvatar = selectedAvatarOpt.get();
                selectedAvatar.setDirectory(resource.getDirectory().getBytes());
                selectedAvatar.setFileName(fileDataExtractor.blobToByteArray(resource.getFileName()));
                selectedAvatar.setHash(fileDataExtractor.blobToByteArray(resource.getHash()));
                selectedAvatar.setResourceUrl(fileDataExtractor.blobToByteArray(resource.getResourceUrl()));
                userAvatarRepository.save(selectedAvatar);
            } else {
                // Create new avatar
                UserAvatar newAvatar = UserAvatar.builder()
                        .id(UUID.randomUUID())
                        .user(selectedUser)
                        .directory(resource.getDirectory().getBytes())
                        .fileName(fileDataExtractor.blobToByteArray(resource.getFileName()))
                        .hash(fileDataExtractor.blobToByteArray(resource.getHash()))
                        .resourceUrl(fileDataExtractor.blobToByteArray(resource.getResourceUrl()))
                        .build();
                userAvatarRepository.save(newAvatar);
            }

        } catch (Exception e) {
            // Cleanup uploaded resource in case of any failure
            if (resource != null) {
                try {
                    String actualFileName = fileDataExtractor.extractActualFileName(
                            new InputStreamReader(resource.getFileName().getBinaryStream()));
                    fileService.deleteResource(bucketName, directory, actualFileName);
                } catch (Exception ex) {
                    // Log but do not rethrow
                    System.err.println("Failed to cleanup uploaded avatar: " + ex.getMessage());
                }
            }
            throw new InternalServerException("Failed to create/update avatar: " + e.getMessage());
        }
    }
}
