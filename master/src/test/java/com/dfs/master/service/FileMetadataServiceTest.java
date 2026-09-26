package com.dfs.master.service;

import com.dfs.common.enums.FileStatus;
import com.dfs.common.enums.UserRole;
import com.dfs.master.config.DfsProperties;
import com.dfs.master.dto.CreateFileRequest;
import com.dfs.master.dto.FileDetailResponse;
import com.dfs.master.dto.FileResponse;
import com.dfs.master.entity.FileMetadata;
import com.dfs.master.entity.User;
import com.dfs.master.exception.AccessDeniedException;
import com.dfs.master.exception.FileNotFoundException;
import com.dfs.master.repository.FileMetadataRepository;
import com.dfs.master.repository.UserRepository;
import com.dfs.master.security.DfsUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class FileMetadataServiceTest {

    private FileMetadataRepository fileMetadataRepository;
    private UserRepository userRepository;
    private AuditService auditService;
    private DfsProperties dfsProperties;
    private FileMetadataService fileMetadataService;

    private User testUser;
    private DfsUserDetails principal;
    private UUID userId;

    @BeforeEach
    void setUp() {
        fileMetadataRepository = mock(FileMetadataRepository.class);
        userRepository = mock(UserRepository.class);
        auditService = mock(AuditService.class);
        dfsProperties = new DfsProperties(
                4_194_304L,  // 4 MB chunk size
                3,           // replication factor
                5,           // heartbeat interval
                15,          // node failure timeout
                5_368_709_120L, // max file size
                60           // upload session timeout
        );

        fileMetadataService = new FileMetadataService(
                fileMetadataRepository, userRepository, auditService, dfsProperties);

        userId = UUID.randomUUID();
        testUser = createTestUser(userId, "testuser", "test@example.com");
        principal = new DfsUserDetails(userId, "testuser", UserRole.USER);
    }

    @Nested
    @DisplayName("createFile")
    class CreateFileTests {

        @Test
        @DisplayName("Should create file metadata and compute chunk count correctly")
        void shouldCreateFileAndComputeChunks() {
            CreateFileRequest request = new CreateFileRequest("test.txt", 10_000_000L, "text/plain");

            when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
            when(fileMetadataRepository.save(any(FileMetadata.class))).thenAnswer(invocation -> {
                FileMetadata f = invocation.getArgument(0);
                setId(f, UUID.randomUUID());
                setTimestamps(f);
                return f;
            });

            FileDetailResponse response = fileMetadataService.createFile(request, principal);

            assertNotNull(response);
            assertEquals("test.txt", response.fileName());
            assertEquals(10_000_000L, response.fileSize());
            assertEquals("text/plain", response.contentType());
            assertEquals(FileStatus.UPLOADING, response.status());
            // 10MB / 4MB = 2.38 → ceil = 3 chunks
            assertEquals(3, response.totalChunks());
            assertEquals(4_194_304L, response.chunkSize());
            assertEquals("testuser", response.ownerUsername());

            verify(fileMetadataRepository).save(any(FileMetadata.class));
            verify(auditService).logOperation(eq(userId), eq("FILE_CREATE"),
                    any(), isNull(), eq("SUCCESS"), any());
        }

        @Test
        @DisplayName("Should throw when user not found")
        void shouldThrowWhenUserNotFound() {
            CreateFileRequest request = new CreateFileRequest("test.txt", 1000L, "text/plain");
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            assertThrows(AccessDeniedException.class,
                    () -> fileMetadataService.createFile(request, principal));
        }
    }

    @Nested
    @DisplayName("listUserFiles")
    class ListFilesTests {

        @Test
        @DisplayName("Should return paginated files for user")
        void shouldReturnPaginatedFiles() {
            Pageable pageable = PageRequest.of(0, 20);
            FileMetadata file1 = createTestFile(testUser, "file1.txt", 1000L);
            FileMetadata file2 = createTestFile(testUser, "file2.txt", 2000L);
            Page<FileMetadata> page = new PageImpl<>(List.of(file1, file2), pageable, 2);

            when(userRepository.getReferenceById(userId)).thenReturn(testUser);
            when(fileMetadataRepository.findByOwnerAndStatusNot(testUser, FileStatus.DELETED, pageable))
                    .thenReturn(page);

            Page<FileResponse> result = fileMetadataService.listUserFiles(principal, pageable);

            assertEquals(2, result.getTotalElements());
            assertEquals("file1.txt", result.getContent().get(0).fileName());
            assertEquals("file2.txt", result.getContent().get(1).fileName());
        }
    }

    @Nested
    @DisplayName("getFileDetails")
    class GetFileDetailsTests {

        @Test
        @DisplayName("Should return file details for owner")
        void shouldReturnDetailsForOwner() {
            UUID fileId = UUID.randomUUID();
            FileMetadata file = createTestFile(testUser, "doc.pdf", 50000L);
            setId(file, fileId);

            when(userRepository.getReferenceById(userId)).thenReturn(testUser);
            when(fileMetadataRepository.findByIdAndOwner(fileId, testUser))
                    .thenReturn(Optional.of(file));

            FileDetailResponse response = fileMetadataService.getFileDetails(fileId, principal);

            assertEquals(fileId, response.id());
            assertEquals("doc.pdf", response.fileName());
        }

        @Test
        @DisplayName("Should throw FileNotFoundException for wrong owner")
        void shouldThrowForWrongOwner() {
            UUID fileId = UUID.randomUUID();
            when(userRepository.getReferenceById(userId)).thenReturn(testUser);
            when(fileMetadataRepository.findByIdAndOwner(fileId, testUser))
                    .thenReturn(Optional.empty());

            assertThrows(FileNotFoundException.class,
                    () -> fileMetadataService.getFileDetails(fileId, principal));
        }
    }

    @Nested
    @DisplayName("softDeleteFile")
    class SoftDeleteTests {

        @Test
        @DisplayName("Should set file status to DELETING")
        void shouldSetStatusToDeleting() {
            UUID fileId = UUID.randomUUID();
            FileMetadata file = createTestFile(testUser, "old.txt", 1000L);
            setId(file, fileId);
            file.setStatus(FileStatus.ACTIVE);

            when(userRepository.getReferenceById(userId)).thenReturn(testUser);
            when(fileMetadataRepository.findByIdAndOwner(fileId, testUser))
                    .thenReturn(Optional.of(file));
            when(fileMetadataRepository.save(any(FileMetadata.class))).thenAnswer(inv -> inv.getArgument(0));

            fileMetadataService.softDeleteFile(fileId, principal);

            assertEquals(FileStatus.DELETING, file.getStatus());
            verify(fileMetadataRepository).save(file);
            verify(auditService).logOperation(eq(userId), eq("FILE_DELETE"),
                    any(), isNull(), eq("SUCCESS"), any());
        }

        @Test
        @DisplayName("Should throw when file is already deleted")
        void shouldThrowWhenAlreadyDeleted() {
            UUID fileId = UUID.randomUUID();
            FileMetadata file = createTestFile(testUser, "gone.txt", 1000L);
            setId(file, fileId);
            file.setStatus(FileStatus.DELETED);

            when(userRepository.getReferenceById(userId)).thenReturn(testUser);
            when(fileMetadataRepository.findByIdAndOwner(fileId, testUser))
                    .thenReturn(Optional.of(file));

            assertThrows(FileNotFoundException.class,
                    () -> fileMetadataService.softDeleteFile(fileId, principal));
        }
    }

    @Nested
    @DisplayName("restoreFile")
    class RestoreTests {

        @Test
        @DisplayName("Should restore a DELETING file to ACTIVE")
        void shouldRestoreFile() {
            UUID fileId = UUID.randomUUID();
            FileMetadata file = createTestFile(testUser, "restored.txt", 1000L);
            setId(file, fileId);
            file.setStatus(FileStatus.DELETING);

            when(userRepository.getReferenceById(userId)).thenReturn(testUser);
            when(fileMetadataRepository.findByIdAndOwner(fileId, testUser))
                    .thenReturn(Optional.of(file));
            when(fileMetadataRepository.save(any(FileMetadata.class))).thenAnswer(inv -> inv.getArgument(0));

            FileDetailResponse response = fileMetadataService.restoreFile(fileId, principal);

            assertEquals(FileStatus.ACTIVE, response.status());
            verify(auditService).logOperation(eq(userId), eq("FILE_RESTORE"),
                    any(), isNull(), eq("SUCCESS"), any());
        }
    }

    // ── Test helpers ────────────────────────────────────────

    private static User createTestUser(UUID id, String username, String email) {
        User user = new User(username, email, "hashed", UserRole.USER);
        try {
            Field idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return user;
    }

    private static FileMetadata createTestFile(User owner, String fileName, long size) {
        FileMetadata file = new FileMetadata(owner, fileName, size, "application/octet-stream",
                (int) Math.ceil((double) size / 4_194_304L), 4_194_304L);
        setId(file, UUID.randomUUID());
        setTimestamps(file);
        return file;
    }

    private static void setId(Object entity, UUID id) {
        try {
            Field idField = entity.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static void setTimestamps(Object entity) {
        try {
            Instant now = Instant.now();
            for (String fieldName : List.of("createdAt", "updatedAt")) {
                try {
                    Field field = entity.getClass().getDeclaredField(fieldName);
                    field.setAccessible(true);
                    field.set(entity, now);
                } catch (NoSuchFieldException ignored) {
                    // Not all entities have both timestamps
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
