import { Component } from '@angular/core';

/** Left-hand brand panel shared by the sign in and sign up pages. */
@Component({
    selector: 'app-auth-brand',
    standalone: true,
    styles: [`:host { display: contents; }`],
    template: `
    <aside class="auth-aside">
      <div class="auth-logo">
        <img class="logo-img" src="logo-mark.svg" alt="RenewIQ logo" />
        <strong>Renew<span class="neon">IQ</span></strong>
      </div>

      <div class="auth-pitch">
        <h2>Every subscription,<br />under control.</h2>
        <p class="muted">Track renewals, see what you really pay each month, and never get surprised by a charge again.</p>

        <ul class="auth-points">
          <li><span class="tick">✓</span> Monthly and yearly spend at a glance</li>
          <li><span class="tick">✓</span> Renewal dates you will not miss</li>
          <li><span class="tick">✓</span> Pause or cancel with one click</li>
        </ul>

        <div class="auth-preview">
          <div>
            <small class="muted">Next renewal</small>
            <strong>Netflix</strong>
          </div>
          <div class="auth-preview-right">
            <strong>₹649</strong>
            <small class="muted">in 3 days</small>
          </div>
        </div>
      </div>

      <small class="muted">© RenewIQ</small>
    </aside>
  `,
})
export class AuthBrandComponent { }
