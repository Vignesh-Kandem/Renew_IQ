package com.renewiq.service;

import com.renewiq.model.KnowledgeDocument;
import com.renewiq.repository.KnowledgeDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/** Small-library lexical RAG. No embedding credentials or external index required. */
@Service
@Transactional(readOnly = true)
public class KnowledgeService {
    private static final int MAX_BYTES = 200_000;
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]{2,}");
    private static final Set<String> STOP = Set.of("the", "and", "for", "what", "how", "does", "that", "this", "with", "from", "about", "can", "you", "are", "have", "my", "is", "to", "of", "in", "it", "me");
    private final KnowledgeDocumentRepository repository;
    private final CurrentUser current;
    public KnowledgeService(KnowledgeDocumentRepository repository, CurrentUser current) { this.repository = repository; this.current = current; }
    public record DocumentView(Long id, String name, Instant uploadedAt, int characters) { }
    public record Passage(Long documentId, String name, int chunk, String text, double score) { }
    public List<DocumentView> list() {
        return repository.findByUserIdOrderByUploadedAtDesc(current.id()).stream().map(d -> new DocumentView(d.getId(), d.getName(), d.getUploadedAt(), d.getContent().length())).toList();
    }
    @Transactional
    public DocumentView upload(MultipartFile file) {
        String name = Optional.ofNullable(file.getOriginalFilename()).orElse("document.txt").replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        if (name.length() > 180 || !(name.toLowerCase(Locale.ROOT).endsWith(".txt") || name.toLowerCase(Locale.ROOT).endsWith(".md")))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload a UTF-8 .txt or .md file with a name under 180 characters.");
        if (file.isEmpty() || file.getSize() > MAX_BYTES)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Documents must contain text and be at most 200 KB.");
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(file.getBytes())).toString().replace("\uFEFF", "").trim();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The document must contain valid UTF-8 text.");
        }
        if (text.isBlank() || text.indexOf('\0') >= 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The document must contain readable text.");
        if (repository.countByUserId(current.id()) >= 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The demo library is limited to 100 documents. Delete an old document first.");
        KnowledgeDocument d = new KnowledgeDocument();
        d.setName(name); d.setContent(text); d.setUser(current.get()); repository.save(d);
        return new DocumentView(d.getId(), d.getName(), d.getUploadedAt(), text.length());
    }
    @Transactional
    public void delete(long id) {
        KnowledgeDocument d = repository.findByIdAndUserId(id, current.id()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found."));
        repository.delete(d);
    }
    public List<Passage> search(String query) {
        return search(query, null);
    }
    public List<Passage> search(String query, List<Long> documentIds) {
        Set<String> terms = terms(query);
        if (terms.isEmpty()) return List.of();
        if (documentIds != null && documentIds.isEmpty()) return List.of();
        List<Passage> results = new ArrayList<>();
        List<KnowledgeDocument> documents = documentIds == null
                ? repository.findByUserIdOrderByUploadedAtDesc(current.id())
                : repository.findByUserIdAndIdInOrderByUploadedAtDesc(current.id(), documentIds);
        for (KnowledgeDocument d : documents) {
            List<String> chunks = chunks(d.getContent());
            for (int i = 0; i < chunks.size(); i++) {
                String text = chunks.get(i);
                Set<String> words = terms(d.getName() + " " + text);
                long matches = terms.stream().filter(words::contains).count();
                if (matches > 0) results.add(new Passage(d.getId(), d.getName(), i + 1, text, (double) matches / terms.size()));
            }
        }
        return results.stream().sorted(Comparator.comparingDouble(Passage::score).reversed()
                .thenComparing(Passage::documentId).thenComparingInt(Passage::chunk)).limit(4).toList();
    }
    static Set<String> terms(String text) {
        Set<String> words = new HashSet<>();
        var matcher = WORD.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) if (!STOP.contains(matcher.group())) words.add(matcher.group());
        return words;
    }
    static List<String> chunks(String text) {
        List<String> chunks = new ArrayList<>();
        for (int start = 0; start < text.length();) {
            int end = Math.min(start + 1200, text.length());
            chunks.add(text.substring(start, end));
            if (end == text.length()) break;
            start = end - 200;
        }
        return chunks;
    }
}
