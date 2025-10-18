package com.baksart.Note2TexBack.project.dto;

import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

public class ProjectUpdateForm {
    @Size(max = 200, message = "Название не должно превышать 200 символов")
    private String name;

    @Size(max = 2000, message = "Описание не должно превышать 2000 символов")
    private String description;

    private MultipartFile latexFile;

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

    public boolean hasName() {
        return name != null;
    }

    public boolean hasDescription() {
        return description != null;
    }

    public boolean hasLatexFile() {
        return latexFile != null && !latexFile.isEmpty();
    }

    public boolean hasPdfFile() {
        return pdfFile != null && !pdfFile.isEmpty();
    }
}
