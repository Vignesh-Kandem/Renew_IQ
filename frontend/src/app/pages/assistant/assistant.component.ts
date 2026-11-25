import { Component, ElementRef, OnDestroy, ViewChild, computed, inject, signal } from '@angular/core';
import { of, switchMap } from 'rxjs';
import { ChatComposerComponent } from './chat-composer.component';
import { AssistantChartComponent } from './assistant-chart.component';
import { ConversationHistoryComponent } from './conversation-history.component';
import { HttpErrorResponse } from '@angular/common/http';
import { AssistantService } from '../../services/assistant.service';
import { ClickSoundService } from '../../services/click-sound.service';
import { AuthService } from '../../services/auth.service';
import { PendingAction, KnowledgeDocument, DocumentSource, AssistantChart, ConversationEntry, SavedConversation } from '../../models/assistant.model';

interface UiMsg {
    id?: number;
    role: 'user' | 'assistant';
    content: string;
    error?: boolean;
    sources?: DocumentSource[];
    charts?: AssistantChart[];
    attachments?: KnowledgeDocument[];
    action?: PendingAction;
    actionState?: 'pending' | 'busy' | 'done' | 'dismissed';
}

@Component({
    selector: 'app-assistant',
    standalone: true,
    imports: [ChatComposerComponent, AssistantChartComponent, ConversationHistoryComponent],
    templateUrl: './assistant.component.html',
    styleUrl: './assistant.component.scss',
})
export class AssistantComponent implements OnDestroy {
    private api = inject(AssistantService);
    private auth = inject(AuthService);
    private sound = inject(ClickSoundService);

    @ViewChild('scroller') scroller?: ElementRef<HTMLDivElement>;

    messages = signal<UiMsg[]>([]);
    sending = signal(false);
    conversations = signal<ConversationEntry[]>([]);
    conversationId = signal<number | null>(null);
    historyLoading = signal(false);
    recovering = signal(false);
    private destroyed=false;
    private navigationVersion=0;
    private poll?: ReturnType<typeof setTimeout>;
    private pendingTurn?: { conversationId: number; content: string; documentIds: string; requestId: string };
    draft = '';
    documents = signal<KnowledgeDocument[]>([]);
    documentBusy = signal(false);
    documentMessage = signal('');
    documentError = signal(false);
    selectedIds = signal<number[]>([]);
    selectedDocuments = computed(() => this.documents().filter(d => this.selectedIds().includes(d.id)));
    @ViewChild(ChatComposerComponent) composer?: ChatComposerComponent;

    toggleDocument(id: number): void {
        this.selectedIds.update(ids => ids.includes(id) ? ids.filter(value => value !== id) : [...ids, id]);
    }

    constructor() { this.loadDocuments(); this.loadConversations(true); }

    private get storageKey(): string { return 'renewiq_last_chat_' + this.auth.user()?.id; }
    private remember(id: number | null): void {
        try { if(id===null)localStorage.removeItem(this.storageKey);else localStorage.setItem(this.storageKey,String(id)); } catch { }
    }
    private loadConversations(restore = false): void {
        const version=this.navigationVersion;
        this.api.conversations().subscribe({
            next: entries => {
                if(this.destroyed)return;
                this.conversations.set(entries);
                if(restore)this.historyLoading.set(false);
                if(restore && version===this.navigationVersion) {
                    let id=0;try{id=Number(localStorage.getItem(this.storageKey));}catch{ }
                    if(entries.some(entry=>entry.id===id))this.openConversation(id);
                }
            },
            error: e => { if(restore)this.historyLoading.set(false); this.documentMessage.set('Could not load saved chats. '+this.errorMessage(e)); this.documentError.set(true); },
        });
    }
    openConversation(id: number): void {
        if(this.destroyed||this.sending()||this.documentBusy()||this.historyLoading())return;
        const version=++this.navigationVersion;
        clearTimeout(this.poll);this.recovering.set(false);this.historyLoading.set(true);
        this.api.conversation(id).subscribe({
            next: view => {if(this.destroyed||version!==this.navigationVersion)return;this.applyConversation(view);this.historyLoading.set(false);this.draft='';this.documentMessage.set('');this.documentError.set(false);this.composer?.reset();},
            error: e => {this.historyLoading.set(false);this.documentMessage.set(this.errorMessage(e));this.documentError.set(true);},
        });
    }
    private applyConversation(view: SavedConversation): void {
        this.conversationId.set(view.id);this.remember(view.id);
        this.messages.set(view.messages.map(m=>({id:m.id,role:m.role,content:m.content,...m.metadata})));
        this.selectedIds.set(view.documentIds);this.recovering.set(view.processing);
        clearTimeout(this.poll);
        if(view.processing)this.poll=setTimeout(()=>this.pollConversation(view.id),3000);
        this.scrollDown();
    }
    private pollConversation(id:number):void {
        if(this.destroyed||this.conversationId()!==id)return;
        const version=this.navigationVersion;
        this.api.conversation(id).subscribe({
            next:view=>{if(!this.destroyed&&version===this.navigationVersion&&this.conversationId()===id)this.applyConversation(view);},
            error:()=>{this.recovering.set(false);this.documentMessage.set('Could not refresh this reply. Reopen the saved chat to check its latest state.');this.documentError.set(true);},
        });
    }
    ngOnDestroy():void{this.destroyed=true;clearTimeout(this.poll);}

    private errorMessage(e: HttpErrorResponse): string {
        return e.status === 0 ? 'Cannot reach the server. Is the backend running on port 8081?'
            : e.error?.message ?? e.error?.detail ?? 'The request failed. Please try again.';
    }

    private loadDocuments(): void {
        this.api.documents().subscribe({
            next: documents => this.documents.set(documents),
            error: e => { this.documentMessage.set(this.errorMessage(e)); this.documentError.set(true); },
        });
    }

    uploadDocument(event: Event): void {
        const input = event.target as HTMLInputElement;
        const file = input.files?.[0];
        if (!file || this.documentBusy() || this.sending()) return;
        this.documentError.set(false);
        input.value = '';
        if (!/\.(txt|md)$/i.test(file.name) || file.size > 200000 || file.size === 0) {
            this.documentError.set(true);
            this.documentMessage.set('Choose a nonempty .txt or .md file up to 200 KB.');
            return;
        }
        this.documentBusy.set(true);
        this.documentMessage.set('Uploading document…');
        this.api.upload(file).subscribe({
            next: d => {
                this.documents.update(list => [...list, d]);
                this.selectedIds.update(ids => [...ids, d.id]);
                this.composer?.focus();
                this.documentMessage.set(`${d.name} is ready for questions.`);
                this.documentBusy.set(false);
            },
            error: e => { this.documentMessage.set(this.errorMessage(e)); this.documentError.set(true); this.documentBusy.set(false); },
        });
    }

    deleteDocument(id: number): void {
        if (this.documentBusy() || this.sending()) return;
        this.documentError.set(false);
        this.documentBusy.set(true);
        this.api.deleteDocument(id).subscribe({
            next: () => {
                this.documents.update(list => list.filter(d => d.id !== id));
                this.selectedIds.update(ids => ids.filter(value => value !== id));
                this.documentMessage.set('Document deleted. Start a new chat to clear earlier excerpts.');
                this.documentBusy.set(false);
            },
            error: e => { this.documentMessage.set(this.errorMessage(e)); this.documentError.set(true); this.documentBusy.set(false); },
        });
    }

    firstName = computed(() => (this.auth.user()?.name ?? '').trim().split(' ')[0]);

    suggestions = [
        'Chart my monthly spending by category',
        'Graph my renewals over the next 30 days',
        'Compare subscription costs in a chart',
        'Visualize my 6-month spending estimate',
    ];

    send(text: string = this.draft): void {
        const content = text.trim();
        if (!content || this.sending() || this.documentBusy() || this.historyLoading() || this.recovering()) return;
        this.composer?.reset();
        const documentIds=[...this.selectedIds()];
        this.draft='';
        this.sending.set(true);
        this.messages.update(items=>[...items,{role:'user',content,attachments:this.selectedDocuments()}]);
        this.scrollDown();
        const existing=this.conversationId();
        const conversation=existing===null?this.api.createConversation():of({id:existing});
        conversation.pipe(switchMap(entry=>{
            this.conversationId.set(entry.id);this.remember(entry.id);
            const selection=JSON.stringify(documentIds);
            const same=this.pendingTurn?.conversationId===entry.id&&this.pendingTurn.content===content&&this.pendingTurn.documentIds===selection;
            const requestId=same?this.pendingTurn!.requestId:crypto.randomUUID();
            this.pendingTurn={conversationId:entry.id,content,documentIds:selection,requestId};
            return this.api.sendMessage(entry.id,content,documentIds,requestId);
        })).subscribe({
            next:view=>{
                this.pendingTurn=undefined;
                this.sending.set(false);this.applyConversation(view);this.loadConversations();
            },
            error:(e:HttpErrorResponse)=>{
                this.sending.set(false);this.draft=content;
                this.documentMessage.set(this.errorMessage(e)+' Your draft is restored. Reopen this saved chat to check whether the server received it.');
                this.documentError.set(true);
                this.pushError(this.errorMessage(e));
                this.loadConversations();
            },
        });
    }

    confirm(i:number):void{this.resolveAction(i,true);}
    dismiss(i:number):void{this.resolveAction(i,false);}
    private resolveAction(i:number,confirm:boolean):void {
        const message=this.messages()[i], id=this.conversationId();
        if(!message?.id||!message.action||id===null||this.sending())return;
        const selected=[...this.selectedIds()];
        this.patch(i,{actionState:'busy'});this.sending.set(true);
        this.api.resolveAction(id,message.id,confirm).subscribe({
            next:view=>{this.sending.set(false);this.applyConversation(view);this.selectedIds.set(selected);},
            error:e=>{this.sending.set(false);this.patch(i,{actionState:'pending'});this.pushError(this.errorMessage(e));},
        });
    }

    clear(): void {
        if (this.sending() || this.documentBusy()) return;
        ++this.navigationVersion;
        clearTimeout(this.poll);
        this.conversationId.set(null);
        this.remember(null);
        this.pendingTurn=undefined;
        this.recovering.set(false);
        this.historyLoading.set(false);
        this.messages.set([]);
        this.selectedIds.set([]);
        this.draft = '';
        this.documentMessage.set('');
        this.documentError.set(false);
        this.composer?.reset();
        this.composer?.focus();
    }

    deleteConversation(id: number): void {
        if(this.sending()||this.documentBusy()||this.historyLoading())return;
        this.historyLoading.set(true);
        this.api.deleteConversation(id).subscribe({
            next:()=>{
                this.historyLoading.set(false);
                this.conversations.update(entries=>entries.filter(entry=>entry.id!==id));
                if(this.conversationId()===id)this.clear();
                this.documentMessage.set('Chat deleted.');this.documentError.set(false);
                void this.sound.play('delete');
            },
            error:e=>{
                this.historyLoading.set(false);this.documentMessage.set(this.errorMessage(e));this.documentError.set(true);
            },
        });
    }

    renameConversation(change: { id: number; title: string }): void {
        if(this.sending()||this.documentBusy()||this.historyLoading())return;
        this.historyLoading.set(true);
        this.api.renameConversation(change.id,change.title).subscribe({
            next:entry=>{
                this.conversations.update(entries=>entries.map(item=>item.id===entry.id?entry:item));
                this.historyLoading.set(false);
                this.documentMessage.set('Chat renamed.');this.documentError.set(false);
            },
            error:e=>{this.historyLoading.set(false);this.documentMessage.set(this.errorMessage(e));this.documentError.set(true);},
        });
    }

    verb(status: string): string {
        return status === 'PAUSED' ? 'Pause' : status === 'CANCELLED' ? 'Cancel' : 'Resume';
    }

    private patch(i: number, change: Partial<UiMsg>): void {
        this.messages.update(m => m.map((x, idx) => (idx === i ? { ...x, ...change } : x)));
    }

    private pushError(text: string): void {
        this.messages.update(m => [...m, { role: 'assistant', content: text, error: true }]);
        this.scrollDown();
    }

    private scrollDown(): void {
        setTimeout(() => {
            const el = this.scroller?.nativeElement;
            if (el) el.scrollTop = el.scrollHeight;
        });
    }
}
