package com.dfs.master.controller;

import com.dfs.common.enums.FileStatus;
import com.dfs.common.enums.UserRole;
import com.dfs.master.dto.CreateFileRequest;
import com.dfs.master.dto.FileDetailResponse;
import com.dfs.master.dto.FileResponse;
import com.dfs.master.security.DfsUserDetails;
import com.dfs.master.service.FileMetadataService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class FileControllerTest {

    @Mock
    private FileMetadataService fileMetadataService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UUID userId = UUID.randomUUID();
    private final DfsUserDetails testPrincipal = new DfsUserDetails(userId, "testuser", UserRole.USER);

    @BeforeEach
    void setUp() {
        HandlerMethodArgumentResolver authPrincipalResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter,
                                          ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest,
                                          WebDataBinderFactory binderFactory) {
                return testPrincipal;
            }
        };

        FileController controller = new FileController(fileMetadataService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(authPrincipalResolver, new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    @DisplayName("POST /api/files should create file and return 201")
    void shouldCreateFile() throws Exception {
        UUID fileId = UUID.randomUUID();
        CreateFileRequest request = new CreateFileRequest("document.pdf", 1024L, "application/pdf");

        FileDetailResponse detail = new FileDetailResponse(
                fileId,
                "document.pdf",
                1024L,
                "application/pdf",
                FileStatus.UPLOADING,
                1,
                4194304L,
                null,
                "testuser",
                Instant.now(),
                Instant.now()
        );

        when(fileMetadataService.createFile(any(CreateFileRequest.class), any(DfsUserDetails.class)))
                .thenReturn(detail);

        mockMvc.perform(post("/api/files")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(fileId.toString()))
                .andExpect(jsonPath("$.data.fileName").value("document.pdf"));
    }

    @Test
    @DisplayName("GET /api/files should return paginated list of files")
    void shouldListFiles() throws Exception {
        UUID fileId = UUID.randomUUID();
        FileResponse fileResponse = new FileResponse(
                fileId,
                "notes.txt",
                256L,
                "text/plain",
                FileStatus.ACTIVE,
                1,
                Instant.now(),
                Instant.now()
        );

        Page<FileResponse> page = new PageImpl<>(List.of(fileResponse));
        when(fileMetadataService.listUserFiles(any(DfsUserDetails.class), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/files"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].fileName").value("notes.txt"));
    }

    @Test
    @DisplayName("GET /api/files/{id} should return file details")
    void shouldGetFileDetails() throws Exception {
        UUID fileId = UUID.randomUUID();
        FileDetailResponse detail = new FileDetailResponse(
                fileId,
                "report.docx",
                5000L,
                "application/vnd.openxmlformats",
                FileStatus.ACTIVE,
                2,
                4194304L,
                "sha256-hash",
                "testuser",
                Instant.now(),
                Instant.now()
        );

        when(fileMetadataService.getFileDetails(eq(fileId), any(DfsUserDetails.class)))
                .thenReturn(detail);

        mockMvc.perform(get("/api/files/{id}", fileId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fileName").value("report.docx"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("DELETE /api/files/{id} should soft-delete file")
    void shouldDeleteFile() throws Exception {
        UUID fileId = UUID.randomUUID();

        mockMvc.perform(delete("/api/files/{id}", fileId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(fileMetadataService).softDeleteFile(eq(fileId), any(DfsUserDetails.class));
    }
}
