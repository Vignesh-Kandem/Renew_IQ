package com.renewiq.service;

import com.renewiq.model.KnowledgeDocument;
import com.renewiq.repository.KnowledgeDocumentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KnowledgeServiceTest {
    private final KnowledgeDocumentRepository repository = mock(KnowledgeDocumentRepository.class);
    private final CurrentUser current = mock(CurrentUser.class);
    private final KnowledgeService service = new KnowledgeService(repository, current);
    @org.junit.jupiter.api.BeforeEach void identity() {
        when(current.id()).thenReturn(9L);
        var user = new com.renewiq.model.AppUser(); user.setId(9L);
        when(current.get()).thenReturn(user);
    }

    @Test void ranksRelevantPassagesAndExcludesUnrelatedContent() {
        KnowledgeDocument policy = document(1L, "policy.md", "Netflix cancellation requires thirty days notice.");
        KnowledgeDocument other = document(2L, "music.md", "Spotify family plans include six accounts.");
        when(repository.findByUserIdOrderByUploadedAtDesc(9L)).thenReturn(List.of(other, policy));
        var result = service.search("What is the Netflix cancellation notice?");
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).documentId());
        assertTrue(service.search("unrelated bicycles").isEmpty());
        assertTrue(service.search("what is my").isEmpty());
    }

    @Test void chunksCoverAllTextWithOverlapAndBoundedSize() {
        String text = "a".repeat(3500);
        var result = KnowledgeService.chunks(text);
        assertEquals(4, result.size());
        assertTrue(result.stream().allMatch(chunk -> chunk.length() <= 1200));
        assertEquals(text, result.get(0) + result.get(1).substring(200)
                + result.get(2).substring(200) + result.get(3).substring(200));
    }

    @Test void rejectsUnsupportedMalformedEmptyAndOversizedFiles() {
        assertThrows(ResponseStatusException.class, () -> service.upload(file("terms.pdf", "text".getBytes())));
        assertThrows(ResponseStatusException.class, () -> service.upload(file("terms.txt", new byte[]{(byte) 0xC3, 0x28})));
        assertThrows(ResponseStatusException.class, () -> service.upload(file("terms.txt", "  ".getBytes())));
        assertThrows(ResponseStatusException.class, () -> service.upload(file("terms.txt", new byte[200001])));
        verify(repository, never()).save(any());
    }

    @Test void uploadStoresTextAndNormalizesFilename() {
        when(repository.save(any())).thenAnswer(invocation -> {
            KnowledgeDocument d = invocation.getArgument(0); d.setId(5L); return d;
        });
        var result = service.upload(file("../terms.md", "Cancellation takes 30 days.".getBytes()));
        assertEquals("terms.md", result.name());
        assertEquals(5L, result.id());
        verify(repository).save(argThat(d -> d.getContent().equals("Cancellation takes 30 days.")));
    }

    @Test void deletedDocumentIsNotRetrieved() {
        var d = document(1L, "terms.txt", "Netflix cancellation policy");
        when(repository.findByIdAndUserId(1L, 9L)).thenReturn(java.util.Optional.of(d));
        service.delete(1L);
        verify(repository).delete(d);
        when(repository.findByUserIdOrderByUploadedAtDesc(9L)).thenReturn(List.of());
        assertTrue(service.search("Netflix").isEmpty());
    }

    @Test void selectedAttachmentsAreRestrictedToTheCurrentUsersDocuments() {
        var d = document(3L, "terms.txt", "Netflix cancellation notice");
        when(repository.findByUserIdAndIdInOrderByUploadedAtDesc(9L, List.of(3L))).thenReturn(List.of(d));
        assertEquals(3L, service.search("Netflix", List.of(3L)).get(0).documentId());
        assertTrue(service.search("Netflix", List.of()).isEmpty());
        verify(repository, never()).findByUserIdOrderByUploadedAtDesc(any());
        when(repository.findByUserIdAndIdInOrderByUploadedAtDesc(9L, List.of(999L))).thenReturn(List.of());
        assertTrue(service.search("Netflix", List.of(999L)).isEmpty());
    }

    private static MockMultipartFile file(String name, byte[] bytes) {
        return new MockMultipartFile("file", name, "text/plain", bytes);
    }
    private static KnowledgeDocument document(Long id, String name, String text) {
        var d = new KnowledgeDocument(); d.setId(id); d.setName(name); d.setContent(text); return d;
    }
}
