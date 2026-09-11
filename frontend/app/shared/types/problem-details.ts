/**
 * Giao diện lỗi Problem Details chuẩn RFC 9457 mở rộng từ Backend Spring Boot.
 */
export interface ProblemDetails {
  type?: string;
  title: string;
  status: number;
  detail: string;
  instance?: string;
  code?: string;
  traceId?: string;
  errors?: Array<{
    field: string;
    code: string;
    message: string;
  }>;
}

export class ApiProblemException extends Error {
  problem: ProblemDetails;

  constructor(problem: ProblemDetails) {
    super(problem.detail || problem.title);
    this.name = 'ApiProblemException';
    this.problem = problem;
  }
}
