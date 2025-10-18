package com.baksart.Note2TexBack.project.dto;

import java.time.Instant;

public record ProjectResponse(
        Long id,
        String name,
        String description,
        Instant createdAt,
        String latexUrl,
        String pdfUrl
) { }
