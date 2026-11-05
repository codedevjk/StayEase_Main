import { Component, Input } from '@angular/core';

@Component({
  selector: 'app-loading-spinner',
  template: `
    <div class="d-flex flex-column justify-content-center align-items-center py-5">
      <div class="spinner-border text-primary" role="status" style="width: 3rem; height: 3rem;">
        <span class="visually-hidden">Loading...</span>
      </div>
      <p class="mt-3 text-muted fw-semibold">{{ message }}</p>
    </div>
  `
})
export class LoadingSpinnerComponent {
  @Input() message: string = 'Loading StayEase data...';
}
