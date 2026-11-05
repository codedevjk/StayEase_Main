import { Component, EventEmitter, Input, Output } from '@angular/core';

@Component({
  selector: 'app-error-state',
  template: `
    <div class="alert alert-danger d-flex align-items-center p-3 rounded-3 my-3 shadow-sm" role="alert">
      <i class="bi bi-exclamation-triangle-fill fs-3 me-3 text-danger"></i>
      <div class="flex-grow-1">
        <h4 class="alert-heading h6 fw-bold mb-1">{{ title }}</h4>
        <div class="small mb-0">{{ message }}</div>
      </div>
      <button *ngIf="retryable" (click)="onRetry.emit()" class="btn btn-sm btn-outline-danger ms-3">
        <i class="bi bi-arrow-clockwise me-1"></i> Retry
      </button>
    </div>
  `
})
export class ErrorStateComponent {
  @Input() title: string = 'Operation Failed';
  @Input() message: string = 'An unexpected error occurred.';
  @Input() retryable: boolean = true;
  @Output() onRetry = new EventEmitter<void>();
}
