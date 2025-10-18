package com.baksart.Note2TexBack.project;

import com.baksart.Note2TexBack.project.dto.ProjectCreateForm;
import com.baksart.Note2TexBack.project.dto.ProjectResponse;
import com.baksart.Note2TexBack.project.dto.ProjectUpdateForm;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/projects")
@Validated
public class ProjectController {
    private final ProjectService service;

    public ProjectController(ProjectService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProjectResponse create(@AuthenticationPrincipal Long userId,
                                  @Valid @ModelAttribute ProjectCreateForm form) {
        return service.create(userId, form);
    }

    @GetMapping
    public List<ProjectResponse> list(@AuthenticationPrincipal Long userId) {
        return service.list(userId);
    }

    @GetMapping("/{id}")
    public ProjectResponse get(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return service.get(userId, id);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProjectResponse update(@AuthenticationPrincipal Long userId,
                                  @PathVariable Long id,
                                  @Valid @ModelAttribute ProjectUpdateForm form) {
        return service.update(userId, id, form);
    }
}
