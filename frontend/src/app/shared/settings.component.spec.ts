import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { SettingsComponent } from './settings.component';
import { AuthService } from '../services/auth.service';
import { ClickSoundService } from '../services/click-sound.service';
import { ThemeService } from '../services/theme.service';

describe('Settings switches',()=>{
  it('updates sound and theme with accessible switch states',()=>{
    const sound={enabled:signal(true),toggle(){this.enabled.update(v=>!v);}};
    const theme={theme:signal('light'),setTheme(value:string){this.theme.set(value);}};
    TestBed.configureTestingModule({imports:[SettingsComponent],providers:[
      {provide:AuthService,useValue:{isLoggedIn:()=>false}},
      {provide:ClickSoundService,useValue:sound},{provide:ThemeService,useValue:theme},
    ]});
    const fixture=TestBed.createComponent(SettingsComponent);
    fixture.componentInstance.open.set(true);fixture.detectChanges();
    const switches=fixture.nativeElement.querySelectorAll('[role="switch"]') as NodeListOf<HTMLButtonElement>;
    expect(switches.length).toBe(2);
    expect(switches[0].getAttribute('aria-checked')).toBe('true');
    switches[0].click();fixture.detectChanges();
    expect(sound.enabled()).toBeFalse();
    expect(switches[0].getAttribute('aria-checked')).toBe('false');
    switches[1].click();fixture.detectChanges();
    expect(theme.theme()).toBe('dark');
    expect(switches[1].getAttribute('aria-checked')).toBe('true');
    switches[1].click();fixture.detectChanges();expect(theme.theme()).toBe('light');
  });
});
