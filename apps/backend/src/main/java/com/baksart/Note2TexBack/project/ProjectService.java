package com.baksart.Note2TexBack.project;

import com.baksart.Note2TexBack.config.S3Config.S3Props;
import com.baksart.Note2TexBack.project.dto.ProjectCreateForm;
import com.baksart.Note2TexBack.project.dto.ProjectResponse;
import com.baksart.Note2TexBack.project.dto.ProjectUpdateForm;
import com.baksart.Note2TexBack.user.User;
import com.baksart.Note2TexBack.user.UserRepo;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
public class ProjectService {
    private final ProjectRepo projects;
    private final UserRepo users;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3Props s3Props;

    public ProjectService(ProjectRepo projects, UserRepo users, S3Client s3Client,
                          S3Presigner s3Presigner, S3Props s3Props) {
        this.projects = projects;
        this.users = users;
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.s3Props = s3Props;
    }

    @Transactional
    public ProjectResponse create(Long userId, ProjectCreateForm form) {
        var user = loadUser(userId);
        var project = new Project();
        project.setOwner(user);
        project.setName(requireName(form.getName()));
        project.setDescription(trimToNull(form.getDescription()));
        project.setLatexKey(uploadFile(form.getLatexFile(), userId, "latex"));
        project.setPdfKey(uploadFile(form.getPdfFile(), userId, "pdf"));
        projects.save(project);
        return toResponse(project);
    }

    @Transactional
    public ProjectResponse update(Long userId, Long projectId, ProjectUpdateForm form) {
        var project = projects.findByIdAndOwner_Id(projectId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Проект не найден"));

        if (form.hasName()) {
            project.setName(requireName(form.getName()));
        }
        if (form.hasDescription()) {
            project.setDescription(trimToNull(form.getDescription()));
        }

        if (form.hasLatexFile()) {
            String oldKey = project.getLatexKey();
            String newKey = uploadFile(form.getLatexFile(), userId, "latex");
            project.setLatexKey(newKey);
            deleteQuietly(oldKey);
        }

        if (form.hasPdfFile()) {
            String oldKey = project.getPdfKey();
            String newKey = uploadFile(form.getPdfFile(), userId, "pdf");
            project.setPdfKey(newKey);
            deleteQuietly(oldKey);
        }

        return toResponse(project);
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> list(Long userId) {
        requireUserId(userId);
        return projects.findAllByOwner_IdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(Long userId, Long projectId) {
        var project = projects.findByIdAndOwner_Id(projectId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Проект не найден"));
        return toResponse(project);
    }

    private ProjectResponse toResponse(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getCreatedAt(),
                presign(project.getLatexKey()),
                presign(project.getPdfKey())
        );
    }

    private String uploadFile(MultipartFile file, Long userId, String kind) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Файл " + kind + " пустой");
        }
        String key = buildKey(userId, kind, file.getOriginalFilename());
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(s3Props.getBucket())
                            .key(key)
                            .contentType(file.getContentType())
                            .build(),
                    RequestBody.fromBytes(file.getBytes())
            );
            return key;
        } catch (IOException | S3Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось загрузить файл", ex);
        }
    }

    private void deleteQuietly(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(s3Props.getBucket())
                    .key(key)
                    .build());
        } catch (S3Exception ignored) {
        }
    }

    private String presign(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        var req = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(15))
                .getObjectRequest(GetObjectRequest.builder()
                        .bucket(s3Props.getBucket())
                        .key(key)
                        .build())
                .build();
        return s3Presigner.presignGetObject(req).url().toString();
    }

    private String buildKey(Long userId, String kind, String originalFilename) {
        String cleanName = originalFilename == null ? "" : originalFilename.trim();
        String ext = "";
        int idx = cleanName.lastIndexOf('.');
        if (idx >= 0 && idx > cleanName.lastIndexOf('/')) {
            ext = cleanName.substring(idx);
        }
        return "users/" + userId + "/projects/" + UUID.randomUUID() + "/" + kind + ext;
    }

    private User loadUser(Long userId) {
        requireUserId(userId);
        return users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Пользователь не найден"));
    }

    private void requireUserId(Long userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Не авторизован");
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Название не может быть пустым");
        }
        return name.trim();
    }
}
