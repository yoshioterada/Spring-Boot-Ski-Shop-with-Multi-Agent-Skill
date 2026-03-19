export interface ProblemDetail {
  type: string;
  title: string;
  status: number;
  detail: string;
  instance: string;
  errorCode?: string;
  timestamp: string;
  errors?: FieldError[];
}

export interface FieldError {
  field: string;
  message: string;
  rejectedValue: unknown;
}

export type ValidationErrorResponse = ProblemDetail & {
  errors: FieldError[];
};

export interface PaginatedResponse<T> {
  content: T[];
  page: {
    size: number;
    number: number;
    totalElements: number;
    totalPages: number;
  };
}

export interface PaginationParams {
  page?: number;
  size?: number;
  sort?: string;
}
