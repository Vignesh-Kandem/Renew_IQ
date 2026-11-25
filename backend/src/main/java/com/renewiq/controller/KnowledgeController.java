package com.renewiq.controller;

import com.renewiq.service.KnowledgeService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpStatus;
import java.util.List;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {
    private final KnowledgeService knowledge;
    public KnowledgeController(KnowledgeService knowledge) { this.knowledge = knowledge; }
    @GetMapping public List<KnowledgeService.DocumentView> list() { return knowledge.list(); }
    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public KnowledgeService.DocumentView upload(@RequestParam("file") MultipartFile file) { return knowledge.upload(file); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) { knowledge.delete(id); }
}
