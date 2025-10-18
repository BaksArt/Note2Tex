package com.baksart.Note2TexBack.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

public class ProjectCreateForm {
    @NotBlank(message = "Название обязательно")
    @Size(max = 200, message = "Название не должно превышать 200 символов")
    private String name;

    @Size(max = 2000, message = "Описание не должно превышать 2000 символов")
    private String description;

    @NotNull(message = "Файл LaTeX обязателен")
    private MultipartFile latexFile;

    @NotNull(message = "PDF-файл обязателен")
    private MultipartFile pdfFile;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public MultipartFile getLatexFile() {
        return latexFile;
    }

    public void setLatexFile(MultipartFile latexFile) {
        this.latexFile = latexFile;
    }

    public MultipartFile getPdfFile() {
        return pdfFile;
    }

    public void setPdfFile(MultipartFile pdfFile) {
        this.pdfFile = pdfFile;
    }
}
