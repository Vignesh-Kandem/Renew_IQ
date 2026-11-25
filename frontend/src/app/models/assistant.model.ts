export interface ChatMessage {
    role: 'user' | 'assistant';
    content: string;
}

export interface PendingAction {
    subscriptionId: number;
    subscriptionName: string;
    status: 'ACTIVE' | 'PAUSED' | 'CANCELLED';
}

export interface AssistantReply {
    reply: string;
    action: PendingAction | null;
    sources?: DocumentSource[];
    charts?: AssistantChart[];
}

export interface AssistantChart {
    type: 'bar' | 'line';
    title: string;
    xLabel: string;
    yLabel: string;
    unit: 'INR';
    asOf: string;
    note: string;
    points: { label: string; value: number }[];
}

export interface DocumentSource {
    documentId: number;
    name: string;
    chunk: number;
    text: string;
}

export interface KnowledgeDocument {
    id: number;
    name: string;
    uploadedAt: string;
    characters: number;
}

export interface ConversationEntry {
    id: number;
    title: string;
    createdAt: string;
    updatedAt: string;
}

export interface SavedMessage {
    id: number;
    role: 'user' | 'assistant';
    content: string;
    createdAt: string;
    metadata: {
        error: boolean;
        attachments?: KnowledgeDocument[];
        sources?: DocumentSource[];
        charts?: AssistantChart[];
        action?: PendingAction;
        actionState?: 'pending' | 'done' | 'dismissed';
    };
}

export interface SavedConversation {
    id: number;
    title: string;
    messages: SavedMessage[];
    documentIds: number[];
    processing: boolean;
}
