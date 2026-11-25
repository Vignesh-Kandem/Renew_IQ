import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AssistantReply, ChatMessage, PendingAction, KnowledgeDocument, ConversationEntry, SavedConversation } from '../models/assistant.model';
import { Subscription } from '../models/subscription.model';

@Injectable({ providedIn: 'root' })
export class AssistantService {
    private http = inject(HttpClient);
    private readonly base = '/api/assistant';

    conversations(): Observable<ConversationEntry[]> { return this.http.get<ConversationEntry[]>(`${this.base}/conversations`); }
    createConversation(): Observable<ConversationEntry> { return this.http.post<ConversationEntry>(`${this.base}/conversations`, {}); }
    deleteConversation(id: number): Observable<void> { return this.http.delete<void>(`${this.base}/conversations/${id}`); }
    renameConversation(id: number, title: string): Observable<ConversationEntry> {
        return this.http.patch<ConversationEntry>(`${this.base}/conversations/${id}`, { title });
    }
    conversation(id: number): Observable<SavedConversation> { return this.http.get<SavedConversation>(`${this.base}/conversations/${id}`); }
    sendMessage(id: number, content: string, documentIds: number[], requestId: string): Observable<SavedConversation> {
        return this.http.post<SavedConversation>(`${this.base}/conversations/${id}/messages`, { content, documentIds, requestId });
    }
    resolveAction(id: number, messageId: number, confirm: boolean): Observable<SavedConversation> {
        return this.http.post<SavedConversation>(`${this.base}/conversations/${id}/messages/${messageId}/action`, { confirm });
    }

    documents(): Observable<KnowledgeDocument[]> {
        return this.http.get<KnowledgeDocument[]>('/api/knowledge');
    }

    upload(file: File): Observable<KnowledgeDocument> {
        const body = new FormData();
        body.append('file', file);
        return this.http.post<KnowledgeDocument>('/api/knowledge', body);
    }

    deleteDocument(id: number): Observable<void> {
        return this.http.delete<void>(`/api/knowledge/${id}`);
    }

    chat(messages: ChatMessage[], documentIds: number[] = []): Observable<AssistantReply> {
        return this.http.post<AssistantReply>(`${this.base}/chat`, { messages, documentIds });
    }

    confirm(action: PendingAction): Observable<Subscription> {
        return this.http.post<Subscription>(`${this.base}/confirm`, {
            subscriptionId: action.subscriptionId,
            status: action.status,
        });
    }
}
