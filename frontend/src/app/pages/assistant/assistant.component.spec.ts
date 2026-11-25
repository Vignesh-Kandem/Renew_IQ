import { TestBed } from '@angular/core/testing';
import { of, Subject } from 'rxjs';
import { AssistantComponent } from './assistant.component';
import { AssistantService } from '../../services/assistant.service';
import { AuthService } from '../../services/auth.service';
import { SavedConversation } from '../../models/assistant.model';

describe('Assistant chat separation', () => {
  const view = (id: number): SavedConversation => ({ id, title: 'Chat '+id, messages: [], documentIds: [], processing: false });
  let api: jasmine.SpyObj<AssistantService>;
  let component: AssistantComponent;
  beforeEach(() => {
    localStorage.removeItem('renewiq_last_chat_987654');
    api = jasmine.createSpyObj('AssistantService', ['documents','conversations','conversation','createConversation','sendMessage']);
    api.documents.and.returnValue(of([]));
    api.conversations.and.returnValue(of([]));
    api.conversation.and.returnValue(of(view(1)));
    api.createConversation.and.returnValue(of({id:2,title:'New chat',createdAt:'',updatedAt:''}));
    api.sendMessage.and.returnValue(of(view(2)));
    TestBed.configureTestingModule({providers:[{provide:AssistantService,useValue:api},{provide:AuthService,useValue:{user:()=>({id:987654})}}]});
    component=TestBed.runInInjectionContext(()=>new AssistantComponent());
  });
  afterEach(()=>{component.ngOnDestroy();localStorage.removeItem('renewiq_last_chat_987654');});
  it('starts a separate conversation after opening a saved chat',()=>{
    component.openConversation(1);
    component.selectedIds.set([10]);
    component.clear();
    expect(component.conversationId()).toBeNull();
    expect(component.selectedIds()).toEqual([]);
    expect(localStorage.getItem('renewiq_last_chat_987654')).toBeNull();
    component.send('A separate topic');
    expect(api.createConversation).toHaveBeenCalledTimes(1);
    expect(api.sendMessage).toHaveBeenCalledWith(2,'A separate topic',[],jasmine.any(String));
  });
  it('ignores a saved-chat response arriving after New chat',()=>{
    const response=new Subject<SavedConversation>();
    api.conversation.and.returnValue(response);
    component.openConversation(1);
    component.clear();
    response.next(view(1));
    expect(component.conversationId()).toBeNull();
    expect(component.messages()).toEqual([]);
  });
});
