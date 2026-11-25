import { Component, ElementRef, EventEmitter, HostListener, Input, Output, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ConversationEntry } from '../../models/assistant.model';

@Component({
  selector:'app-conversation-history', standalone:true, imports:[DatePipe,FormsModule],
  template:`
    <button class="btn-ghost" type="button" [disabled]="busy" aria-controls="chat-history" [attr.aria-expanded]="open()" (click)="open.set(!open())">Saved chats</button>
    @if(open()) {
      <section id="chat-history" aria-label="Saved conversations">
        <header><strong>Your chats</strong><span>{{entries.length}} saved</span></header>
        <div class="entries">
          @for(entry of entries;track entry.id) {
            <div class="chat-row">
              @if(editingId()===entry.id) {
                <form (ngSubmit)="save(entry.id)">
                  <input aria-label="Chat title" name="title" [(ngModel)]="title" maxlength="80" required />
                  <button type="submit" [disabled]="busy || !title.trim()">Save</button>
                  <button type="button" [disabled]="busy" (click)="editingId.set(null)">Cancel</button>
                </form>
              } @else {
                <button type="button" class="entry" [disabled]="busy" [class.active]="entry.id===activeId" [attr.aria-current]="entry.id===activeId?'true':null" (click)="select(entry.id)">
                  <strong>{{entry.title}}</strong><small>{{entry.updatedAt+'Z' | date:'d MMM, HH:mm'}}</small>
                </button>
                <button type="button" class="more" [disabled]="busy" [attr.aria-label]="'Options for '+entry.title" [attr.aria-expanded]="menuId()===entry.id" [attr.aria-controls]="'chat-options-'+entry.id" (click)="toggleMenu(entry.id)">&#8943;</button>
              }
              @if(menuId()===entry.id) {
                <div class="chat-menu" [id]="'chat-options-'+entry.id" aria-label="Chat options">
                  @if(deletingId()===entry.id) {
                    <p>Delete this chat permanently?</p>
                    <button type="button" class="danger" [disabled]="busy" (click)="remove(entry.id)">Delete chat</button>
                    <button type="button" [disabled]="busy" (click)="deletingId.set(null)">Cancel</button>
                  } @else {
                    <button type="button" [disabled]="busy" (click)="edit(entry)">Rename</button>
                    <button type="button" class="danger" [disabled]="busy" (click)="deletingId.set(entry.id)">Delete chat</button>
                  }
                </div>
              }
            </div>
          } @empty {<p>Your first message starts a saved conversation.</p>}
        </div>
        <footer>Chats are private to your account.</footer>
      </section>
    }
  `,
  styles:[`
    :host{position:relative;display:block}section{position:absolute;right:0;top:calc(100% + 10px);width:min(320px,calc(100vw - 40px));background:var(--surface);border:1px solid var(--moss-border);border-radius:14px;box-shadow:0 12px 36px #0003;padding:10px;z-index:25}header{display:flex;justify-content:space-between;padding:10px;font-size:.8rem}header span,footer,small{color:var(--muted);font-size:.65rem}.entries{max-height:340px;overflow:auto}.chat-row{display:flex;flex-wrap:wrap;align-items:center;gap:4px}button{cursor:pointer;background:transparent;color:var(--text);border:0;border-radius:8px;padding:9px}button:disabled{opacity:.5;cursor:default}button:hover,.entry.active{background:var(--surface-2)}.entry{min-width:0;flex:1;text-align:left;padding:12px 10px}.entry strong{display:block;font-size:.78rem;font-weight:500;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}small{display:block;margin-top:5px}.entry.active{box-shadow:inset 2px 0 var(--neon)}.more{font-size:1.4rem}.chat-menu{width:100%;padding:5px;border:1px solid var(--moss-border);border-radius:8px}.chat-menu button{display:block;width:100%;text-align:left}.chat-menu .danger{color:var(--danger)}p,footer{padding:10px;line-height:1.6;font-size:.75rem;color:var(--muted)}footer{border-top:1px solid var(--moss-border)}form{padding:10px;width:100%}input{box-sizing:border-box;width:100%;padding:9px;border:1px solid var(--moss-border);border-radius:6px;background:var(--surface-2);color:var(--text)}
  `],
})
export class ConversationHistoryComponent {
  @Input() entries:ConversationEntry[]=[];
  @Input() activeId:number|null=null;
  @Input() busy=false;
  @Output() selectConversation=new EventEmitter<number>();
  @Output() renameConversation=new EventEmitter<{id:number;title:string}>();
  @Output() deleteConversation=new EventEmitter<number>();
  private element=inject(ElementRef<HTMLElement>);
  open=signal(false);
  menuId=signal<number|null>(null);
  deletingId=signal<number|null>(null);
  editingId=signal<number|null>(null);
  title='';
  toggleMenu(id:number):void{this.menuId.set(this.menuId()===id?null:id);this.deletingId.set(null);}
  edit(entry:ConversationEntry):void{this.menuId.set(null);this.editingId.set(entry.id);this.title=entry.title;}
  save(id:number):void{if(this.busy||!this.title.trim())return;this.renameConversation.emit({id,title:this.title.trim()});this.editingId.set(null);}
  remove(id:number):void{if(this.busy)return;this.deleteConversation.emit(id);this.menuId.set(null);this.deletingId.set(null);}
  select(id:number):void{this.menuId.set(null);this.open.set(false);this.selectConversation.emit(id);}
  @HostListener('document:click',['$event']) outside(event:MouseEvent):void{if(event.target instanceof Node&&!this.element.nativeElement.contains(event.target)){this.open.set(false);this.menuId.set(null);this.deletingId.set(null);}}
  @HostListener('document:keydown.escape') escape():void{if(this.menuId()!==null){this.menuId.set(null);this.deletingId.set(null);}else{this.open.set(false);this.editingId.set(null);}}
}
