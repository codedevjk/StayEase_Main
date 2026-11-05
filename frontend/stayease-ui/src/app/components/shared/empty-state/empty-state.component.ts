import { Component, Input } from '@angular/core';

@Component({
  selector: 'app-empty-state',
  template: `
    <div class="text-center py-5 px-3">
      <div class="mb-3 text-muted display-4">
        <i [class]="icon"></i>
      </div>
      <h3 class="h5 fw-bold text-dark">{{ title }}</h3>
      <p class="text-muted mx-auto" style="max-width: 480px;">{{ message }}</p>
      <div *ngIf="actionText && actionRoute" class="mt-3">
        <a [routerLink]="actionRoute" class="btn btn-primary">{{ actionText }}</a>
      </div>
    </div>
  `
})
export class EmptyStateComponent {
  @Input() icon: string = 'bi bi-inbox';
  @Input() title: string = 'No records found';
  @Input() message: string = 'There are no items matching your criteria.';
  @Input() actionText?: string;
  @Input() actionRoute?: string;
}
