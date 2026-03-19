import { HttpError } from './http';

import type { FieldError } from '@/types/api';

export interface FormErrors {
  [field: string]: string;
}

export function parseValidationErrors(error: HttpError): FormErrors {
  if (!error.isValidationError || !error.problemDetail.errors) {
    return {};
  }
  return error.problemDetail.errors.reduce<FormErrors>((acc, err: FieldError) => {
    acc[err.field] = err.message;
    return acc;
  }, {});
}

export function getErrorMessage(error: unknown): string {
  if (error instanceof HttpError) {
    return error.problemDetail.detail;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return 'エラーが発生しました';
}

export function getCorrelationId(error: unknown): string | undefined {
  if (error instanceof HttpError) {
    return error.correlationId;
  }
  return undefined;
}

export function isRetryableError(error: unknown): boolean {
  if (error instanceof HttpError) {
    return error.isRateLimited || error.isServerError;
  }
  return false;
}
