import { Component, ElementRef, EventEmitter, HostListener, Input, Output, ViewChild, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { KnowledgeDocument } from '../../models/assistant.model';

@Component({
  selector: 'app-chat-composer', standalone: true, imports: [FormsModule],
  templateUrl: './chat-composer.component.html', styleUrl: './chat-composer.component.scss',
})
export class ChatComposerComponent {
  @Input() documents: KnowledgeDocument[] = [];
  @Input() selectedIds: number[] = [];
  @Input() sending = false;
  @Input() documentBusy = false;
  @Input() documentMessage = '';
  @Input() documentError = false;
  @Input() draft = '';
  @Output() draftChange = new EventEmitter<string>();
  @Output() sendMessage = new EventEmitter<void>();
  @Output() uploadFile = new EventEmitter<Event>();
  @Output() toggleDocument = new EventEmitter<number>();
  @Output() deleteDocument = new EventEmitter<number>();
  @ViewChild('messageInput') messageInput?: ElementRef<HTMLTextAreaElement>;
  @ViewChild('attachmentButton') attachmentButton?: ElementRef<HTMLButtonElement>;
  private element = inject(ElementRef<HTMLElement>);
  attachmentMenu = signal(false);
  get selectedDocuments(): KnowledgeDocument[] { return this.documents.filter(d => this.selectedIds.includes(d.id)); }
  focus(): void { this.messageInput?.nativeElement.focus(); }
  reset(): void {
    this.attachmentMenu.set(false);
    if (this.messageInput) this.messageInput.nativeElement.style.height = 'auto';
  }
  resizeInput(input: HTMLTextAreaElement): void {
    input.style.height = 'auto'; input.style.height = Math.min(input.scrollHeight, 150) + 'px';
  }
  onEnter(event: KeyboardEvent): void {
    if (!event.shiftKey && !event.isComposing) { event.preventDefault(); this.send(); }
  }
  send(): void {
    if (!this.draft.trim() || this.sending || this.documentBusy) return;
    this.attachmentMenu.set(false); this.sendMessage.emit();
  }
  @HostListener('document:click', ['$event']) outside(event: MouseEvent): void {
    if (!(event.target instanceof Element) || !this.element.nativeElement.contains(event.target.closest('.attachment-control'))) this.attachmentMenu.set(false);
  }
  @HostListener('document:keydown.escape') escape(): void {
    if (this.attachmentMenu()) { this.attachmentMenu.set(false); this.attachmentButton?.nativeElement.focus(); }
  }
}
