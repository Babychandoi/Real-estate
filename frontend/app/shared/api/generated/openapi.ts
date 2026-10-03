/**
 * GENERATED FILE — do not edit. Source: backend/src/test/resources/openapi/openapi.json (npm run gen:api).
 * Use the aliases in app/shared/api/schema.ts rather than importing this file directly.
 */
export type paths = {
  '/api/v1/admin/audit-chain/verification': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['run'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/admin/audit-chain/verification/restart': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['restart'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/admin/listings': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['search_3'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/admin/listings/{id}/history': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['history_3'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/admin/listings/{id}/preview': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['preview_2'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/admin/listings/{id}/revisions': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['revisions'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/admin/listings/{id}/status': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['changeStatus'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/admin/users': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['list_5'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/admin/users/{id}/history': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['history_2'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/admin/users/{id}/kyc-access-log': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['kycAccessLog'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/admin/users/{id}/kyc-documents': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['openKycDocuments'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/admin/users/{id}/mfa/reset': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['resetMfa'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/admin/users/{id}/role': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch: operations['updateRole'];
    trace?: never;
  };
  '/api/v1/admin/users/{id}/sessions/revoke': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['revokeSessions'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/admin/users/{id}/status': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch: operations['updateStatus'];
    trace?: never;
  };
  '/api/v1/analytics/dashboard': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['dashboard'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/analytics/funnel': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['getFunnelAnalytics'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/analytics/lead-funnel': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['leadFunnel'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/analytics/overview': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['getOverviewAnalytics'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/appointments/{id}/cancel': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['cancel_1'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/appointments/{id}/confirm': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['confirm'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/appointments/{id}/outcome': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['outcome'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/auth/admin/login': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['adminLogin'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/auth/admin/mfa/enroll': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['beginEnrollment'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/auth/admin/mfa/enroll/confirm': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['confirmEnrollment'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/auth/admin/mfa/verify': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['verifyMfa'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/auth/forgot-password': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['forgotPassword'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/auth/login': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['login'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/auth/logout': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['logout'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/auth/me': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['me'];
    put: operations['updateMe'];
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/auth/me/avatar': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put: operations['updateAvatar'];
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/auth/password-reset/status': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['passwordResetStatus'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/auth/register': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['register'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/auth/resend-verification': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['resendVerification'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/auth/reset-password': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['resetPassword'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/auth/verify-email': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['verifyEmail'];
    put?: never;
    post: operations['verifyEmailPost'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/billing/admin/bank': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['bank'];
    put: operations['bank_1'];
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/billing/admin/orders': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['orders'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/billing/admin/orders/{id}': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['orderDetail'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/billing/admin/reconciliation': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['queue_1'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/billing/admin/reconciliation/{id}/approve': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['approve_2'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/billing/admin/reconciliation/{id}/receipt': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['receipt'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/billing/admin/reconciliation/{id}/reject': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['reject_2'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/billing/admin/reconciliation/{id}/resolve': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['resolve'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/billing/orders': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['mine_1'];
    put?: never;
    post: operations['create_3'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/billing/orders/{id}': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['mineDetail'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/billing/orders/{id}/cancel': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['cancel'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/billing/orders/{id}/reported': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['report'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/billing/plans': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['plans'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/broker/reports/leads': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['leadReport'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/broker/team': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['team'];
    put?: never;
    post: operations['addMember'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/broker/team/{memberId}': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post?: never;
    delete: operations['removeMember_1'];
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/broker/workspace': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['get_1'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/broker/workspace/sla': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put: operations['sla'];
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/catalog/projects': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['getProjects'];
    put?: never;
    post: operations['createProject'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/catalog/projects/{id}': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['getProjectById'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/catalog/projects/{id}/public-profile': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['profile'];
    put: operations['updateProfile'];
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/catalog/projects/slug/{slug}': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['getProjectBySlug'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/cms/articles': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['list_2'];
    put?: never;
    post: operations['create_2'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/cms/articles/{id}': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['detail_1'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/cms/articles/{id}/revisions': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['newRevision'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/cms/articles/{id}/revisions/{revisionId}': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put: operations['updateDraft_1'];
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/cms/articles/{id}/revisions/{revisionId}/approve': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['approve_1'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/cms/articles/{id}/revisions/{revisionId}/preview-link': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['previewLink'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/cms/articles/{id}/revisions/{revisionId}/reject': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['reject_1'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/cms/articles/{id}/revisions/{revisionId}/submit': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['submit'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/cms/articles/{id}/schedule': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post?: never;
    delete: operations['cancelSchedule'];
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/cms/articles/{id}/unpublish': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['unpublish'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/events': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['ingest'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/events/consent': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['consent'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/kyc/{id}/approve': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['approveKyc'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/kyc/{id}/reject': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['rejectKyc'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/kyc/{id}/revoke': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['revokeKyc'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/kyc/documents/access': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['grantDocumentAccess'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/kyc/me/status': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['myStatus'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/kyc/queue': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['getQueue_2'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/kyc/submit': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['submitKyc'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/kyc/user/{userId}': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['getKycByUserId'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/kyc/user/{userId}/documents': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['getOwnDocuments'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/leads': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['getLeads'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/leads/{id}': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['getLead'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/leads/{id}/appointments': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['leadAppointments'];
    put?: never;
    post: operations['propose_1'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/leads/{id}/assignee': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch: operations['assign'];
    trace?: never;
  };
  '/api/v1/leads/{id}/contact': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['revealLeadContact'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/leads/{id}/history': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['history_1'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/leads/{id}/qualification': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch: operations['qualify'];
    trace?: never;
  };
  '/api/v1/leads/{id}/status': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch: operations['updateLeadStatus'];
    trace?: never;
  };
  '/api/v1/leads/inbox': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['searchLeads_1'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/leads/listings': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['getLeadListings'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/leads/report': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['report_1'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/leads/search': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['searchLeads'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/leads/sent': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['getSentLeads'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/listings': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['createDraft'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/listings/{id}': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['getListingById'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/listings/{id}/draft': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put: operations['updateDraft'];
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/listings/{id}/submit': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['submitRevision'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/listings/{id}/visibility': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['changeVisibility'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/listings/{listingId}/verifications': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['submitVerification'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/listings/admin/all': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['getAllForAdmin'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/listings/by-slug/{slug}': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['getListingBySlug'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/listings/estimate-price': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['estimatePrice'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/listings/my-listings': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['getMyListings'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/listings/quality-score': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['calculateQualityScore'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/listings/search': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['search_2'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/me/become-owner': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['becomeOwner'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/me/inquiries': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['list_4'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/me/inquiries/{id}/appointments': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['appointments'];
    put?: never;
    post: operations['propose'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/me/inquiries/{id}/history': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['history'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/me/inquiries/{id}/withdraw': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['withdraw'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/me/inquiries/eligibility': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['eligibility'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/me/mfa': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['mfaStatus'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/me/mfa/recovery-codes': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['regenerateRecoveryCodes'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/me/notification-preferences': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['list'];
    put: operations['update'];
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/me/password': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['changePassword'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/me/saved-listings': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['page_1'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/me/saved-listings/{listingId}': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put: operations['save'];
    post?: never;
    delete: operations['unsave'];
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/me/saved-listings/ids': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['ids'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/me/saved-searches': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['list_1'];
    put?: never;
    post: operations['create_1'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/me/saved-searches/{id}': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post?: never;
    delete: operations['delete_1'];
    options?: never;
    head?: never;
    patch: operations['update_1'];
    trace?: never;
  };
  '/api/v1/me/security-events': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['securityEvents'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/me/sessions': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['sessions'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/me/sessions/{sessionId}': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post?: never;
    delete: operations['revoke_1'];
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/me/sessions/revoke-others': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['revokeOthers'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/me/shortlists': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['mine'];
    put?: never;
    post: operations['create'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/me/shortlists/{id}': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['get'];
    put?: never;
    post?: never;
    delete: operations['delete'];
    options?: never;
    head?: never;
    patch: operations['rename'];
    trace?: never;
  };
  '/api/v1/me/shortlists/{id}/items/{listingId}': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put: operations['addItem'];
    post?: never;
    delete: operations['removeItem'];
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/me/shortlists/{id}/members/{memberId}': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post?: never;
    delete: operations['removeMember'];
    options?: never;
    head?: never;
    patch: operations['setRole'];
    trace?: never;
  };
  '/api/v1/me/shortlists/{id}/membership': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post?: never;
    delete: operations['leave'];
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/me/shortlists/{id}/mute': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put: operations['mute'];
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/me/shortlists/{id}/share': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['share'];
    delete: operations['revoke'];
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/me/shortlists/join': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['join'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/media/images': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['upload'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/media/images/{objectKey}': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post?: never;
    delete: operations['delete_3'];
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/media/kyc': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['uploadKyc'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/media/kyc/{objectKey}': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['readKyc'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/media/signed-urls': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['sign'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/media/signed/{objectKey}': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['readSigned'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/moderation/audit-samples': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['auditSamples'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/moderation/audit-samples/{id}/review': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['reviewSample'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/moderation/audit-samples/draw': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['drawSamples'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/moderation/bulk': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['bulk'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/moderation/duplicates/{candidateId}': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['decideDuplicate'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/moderation/listings/{id}/approve': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['approve'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/moderation/listings/{id}/claim': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['claim_1'];
    delete: operations['release_1'];
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/moderation/listings/{id}/decisions': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['decisions'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/moderation/listings/{id}/diff': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['getDiff'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/moderation/listings/{id}/duplicates': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['duplicates'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/moderation/listings/{id}/reject': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['reject'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/moderation/queue': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['getQueue_1'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/moderation/reasons': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['reasons_1'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/moderation/rejection-reasons': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['getRejectionReasons'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/notifications': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['recent'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/notifications/{id}': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post?: never;
    delete: operations['delete_2'];
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/notifications/{id}/read': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['read'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/notifications/feed': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['feed'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/notifications/read-all': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['readAll'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/notifications/stream': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['stream'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/notifications/unread-count': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['unreadCount'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/public/articles': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['list_3'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/public/articles/{slug}': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['bySlug_1'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/public/articles/preview/{token}': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['preview_1'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/public/geocoding': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['search_1'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/public/leads': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['submitLead'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/public/media/{objectKey}': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['read_1'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/public/profiles/{ownerId}': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['getProfile'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/public/profiles/{ownerId}/listings': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    /** @deprecated */
    get: operations['listings_1'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/public/reports': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['submitReport'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/public/seo/robots.txt': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['robots'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/public/seo/sitemap.xml': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['index'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/public/seo/sitemaps/{name}.xml': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['part'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/public/shortlists/{token}': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['publicView'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/public/site-info': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['siteInfo'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/public/unsubscribe': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['describe'];
    put?: never;
    post: operations['apply'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/reports': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['getReports'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/reports/{id}': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['getReportById'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/reports/{id}/appeal': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['appealReport'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/reports/{id}/claim': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['claim'];
    delete: operations['release'];
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/reports/{id}/dismiss': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['dismissReport'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/reports/{id}/emergency-hide': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['emergencyHideListing'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/reports/{id}/events': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['events'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/reports/{id}/notes': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['addNote'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/reports/{id}/resolve': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['resolveReport'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/reports/{id}/severity': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['escalate'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/reports/queue': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['queue'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/transactions/deposits': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['createContract'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/transactions/deposits/{id}': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['getContract'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/transactions/deposits/{id}/refund': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['refundEscrow'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/transactions/deposits/{id}/release': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['releaseEscrow'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/transactions/deposits/{id}/sign-buyer': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['signByBuyer'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/transactions/deposits/{id}/sign-seller': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['signBySeller'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/transactions/deposits/listing/{listingId}': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['getContractsByListing'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/verifications': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['getQueue'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/verifications/{id}': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['getVerificationDetail'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/verifications/{id}/approve': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['approveVerification'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/verifications/{id}/document-access': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['openDocuments'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/verifications/{id}/evidence': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['getEvidence'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/verifications/{id}/reject': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['rejectVerification'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/verifications/{id}/revoke': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['revokeVerification'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/verifications/reasons': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['reasons'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v2/admin/media/backfill': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['progress'];
    put?: never;
    post: operations['enqueue'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v2/admin/search/index': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['status'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v2/admin/search/index/cleanup': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['cleanup'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v2/admin/search/index/rebuild': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['rebuild'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v2/admin/search/index/rollback': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['rollback'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v2/listings/{slugOrId}': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['detail'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v2/listings/{slugOrId}/price-history': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['priceHistory'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v2/listings/{slugOrId}/similar': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['similar'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v2/listings/map': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['map'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v2/listings/search': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['search'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v2/me/listings': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['myListings'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v2/me/listings/{id}/confirm-availability': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['confirmAvailability'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v2/me/listings/{id}/draft': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['draft'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v2/me/listings/{id}/preview': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['preview'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v2/me/listings/{id}/renew': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['renew'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v2/me/listings/import': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['importCsv'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v2/me/listings/import/template': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['importTemplate'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v2/public/areas': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['areas'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v2/public/areas/{slug}': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['area'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v2/public/articles': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['page'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v2/public/articles/{slug}': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['bySlug'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v2/public/home': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['home'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v2/public/projects': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['projects'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v2/public/projects/{slug}': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['project'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v2/public/sellers/{sellerId}': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['profile_1'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v2/public/sellers/{sellerId}/listings': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['listings'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/error': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['error'];
    put: operations['error_2'];
    post: operations['error_1'];
    delete: operations['error_3'];
    options: operations['error_6'];
    head: operations['error_5'];
    patch: operations['error_4'];
    trace?: never;
  };
  '/render': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['render'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/render/**': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['render_1'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
};
export type webhooks = Record<string, never>;
export type components = {
  schemas: {
    AccessLogEntry: {
      /** Format: uuid */
      actorId?: string;
      actorName?: string;
      /** Format: date-time */
      createdAt?: string;
      /** Format: date-time */
      grantExpiresAt?: string;
      /** Format: uuid */
      id?: string;
      reason?: string;
    };
    AdminAction: {
      action?: string;
      /** Format: uuid */
      actorId?: string;
      actorName?: string;
      /** Format: date-time */
      createdAt?: string;
      fromValue?: string;
      /** Format: uuid */
      id?: string;
      reason?: string;
      toValue?: string;
    };
    AdminLoginRequest: {
      /** Format: email */
      email: string;
      password: string;
    };
    AdminLoginResult: {
      accessToken?: string;
      /** Format: date-time */
      challengeExpiresAt?: string;
      challengeToken?: string;
      /** Format: date-time */
      expiresAt?: string;
      /** Format: date-time */
      idleExpiresAt?: string;
      mfaRequired?: boolean;
      mfaState?: string;
      user?: components['schemas']['UserView'];
    };
    AdminOrder: {
      /** Format: int64 */
      amountVnd?: number;
      /** Format: date-time */
      createdAt?: string;
      customerEmail?: string;
      customerName?: string;
      exceptionReason?: string;
      /** Format: uuid */
      id?: string;
      planCode?: string;
      planName?: string;
      /** Format: int64 */
      receivedAmountVnd?: number;
      receivedReference?: string;
      reference?: string;
      /** Format: date-time */
      reportedAt?: string;
      resolution?: string;
      /** Format: date-time */
      reviewedAt?: string;
      reviewerName?: string;
      reviewNote?: string;
      status?: string;
      /** Format: uuid */
      userId?: string;
    };
    AdminOrderPage: {
      counts?: {
        [key: string]: number;
      };
      items?: components['schemas']['AdminOrder'][];
      /** Format: int32 */
      page?: number;
      /** Format: int32 */
      size?: number;
      /** Format: int64 */
      total?: number;
    };
    Alert: {
      code?: string;
      message?: string;
      severity?: string;
    };
    Amenity: {
      category?: string;
      /** Format: date */
      checkedAt?: string;
      /** Format: int32 */
      distanceM?: number;
      /** Format: uuid */
      id?: string;
      name?: string;
      sourceName?: string;
      sourceUrl?: string;
    };
    AmenityInput: {
      category?: string;
      /** Format: date */
      checkedAt?: string;
      /** Format: int32 */
      distanceM?: number;
      name?: string;
      sourceName?: string;
      sourceUrl?: string;
    };
    AnalyticsFreshness: {
      aggregateLatency?: string;
      /** Format: date-time */
      aggregatesComputedAt?: string;
      /** Format: date-time */
      latestWebEventAt?: string;
      rawLatency?: string;
    };
    AppealReportRequest: {
      newEvidence: string;
    };
    AppointmentSummary: {
      /** Format: date-time */
      endsAt?: string;
      /** Format: uuid */
      id?: string;
      proposedBySide?: string;
      /** Format: date-time */
      startsAt?: string;
      status?: string;
      /** Format: int64 */
      version?: number;
    };
    AppointmentView: {
      awaitingMe?: boolean;
      /** Format: date-time */
      cancelledAt?: string;
      cancelReason?: string;
      /** Format: date-time */
      confirmedAt?: string;
      /** Format: date-time */
      createdAt?: string;
      /** Format: date-time */
      endsAt?: string;
      /** Format: uuid */
      id?: string;
      /** Format: uuid */
      leadId?: string;
      /** Format: uuid */
      listingId?: string;
      noShowParty?: string;
      note?: string;
      /** Format: date-time */
      outcomeAt?: string;
      proposedBySide?: string;
      slots?: components['schemas']['LeadSlot2'][];
      /** Format: date-time */
      startsAt?: string;
      status?: string;
      /** Format: int64 */
      version?: number;
    };
    ApproveListingRequest: {
      note?: string;
      reasonCode?: string;
      /** Format: uuid */
      revisionId: string;
    };
    ApproveRequest: {
      /** Format: date-time */
      publishAt?: string;
    };
    ApproveVerificationRequest: {
      reasonCode?: string;
      verifierNote?: string;
    };
    AreaCard: {
      /** Format: int64 */
      activeListings?: number;
      districtCode?: string;
      name?: string;
      provinceName?: string;
      slug?: string;
    };
    AreaList: {
      /** Format: date-time */
      dataAsOf?: string;
      items?: components['schemas']['AreaCard'][];
    };
    AreaPage: {
      area?: components['schemas']['AreaRow'];
      inventory?: components['schemas']['Inventory'];
      projects?: components['schemas']['ProjectCard'][];
    };
    AreaRow: {
      districtCode?: string;
      name?: string;
      provinceCode?: string;
      provinceName?: string;
      slug?: string;
    };
    ArticleResponse: {
      /** @enum {string} */
      category?: 'LEGAL_POLICY' | 'KNOWLEDGE' | 'MARKET_INSIGHTS';
      /** Format: date-time */
      createdAt?: string;
      currentRevision?: components['schemas']['RevisionResponse'];
      /** Format: date-time */
      firstPublishedAt?: string;
      /** Format: uuid */
      id?: string;
      publicPath?: string;
      /** Format: date-time */
      publishedAt?: string;
      /** Format: uuid */
      publishedRevisionId?: string;
      /** Format: int32 */
      revisionCount?: number;
      revisions?: components['schemas']['RevisionResponse'][];
      /** Format: date-time */
      scheduledPublishAt?: string;
      /** Format: uuid */
      scheduledRevisionId?: string;
      slug?: string;
      /** @enum {string} */
      status?: 'DRAFT' | 'SUBMITTED' | 'SCHEDULED' | 'PUBLISHED' | 'SUPERSEDED' | 'ARCHIVED' | 'REJECTED';
      /** Format: date-time */
      unpublishedAt?: string;
      /** Format: date-time */
      updatedAt?: string;
    };
    Assign: {
      /** Format: uuid */
      assigneeId?: string;
      /** Format: int64 */
      expectedVersion?: number;
    };
    AuditReviewRequest: {
      note?: string;
      outcome: string;
      reasonCode: string;
    };
    AuthResult: {
      accessToken?: string;
      /** Format: date-time */
      expiresAt?: string;
      /** Format: date-time */
      idleExpiresAt?: string;
      user?: components['schemas']['UserView'];
    };
    BankRequest: {
      accountName?: string;
      accountNumber?: string;
      adminEmail?: string;
      bankBin?: string;
      bankName?: string;
      /** Format: int64 */
      expectedVersion?: number;
    };
    BankSettings: {
      accountName?: string;
      accountNumber?: string;
      adminEmail?: string;
      bankBin?: string;
      bankName?: string;
      /** Format: int64 */
      version?: number;
    };
    BecomeOwnerRequest: {
      confirmOwnProperty?: boolean;
    };
    BecomeOwnerResult: {
      /** @enum {string} */
      outcome?: 'UPGRADED' | 'ALREADY_OWNER';
      user?: components['schemas']['UserView'];
    };
    Breakdown: {
      /** Format: int64 */
      detailViews?: number;
      key?: string;
      /** Format: int64 */
      leadForms?: number;
      /** Format: int64 */
      searchSessions?: number;
    };
    BulkItem: {
      /** Format: uuid */
      listingId?: string;
      /** Format: uuid */
      revisionId?: string;
    };
    BulkItemResult: {
      /** Format: uuid */
      listingId?: string;
      message?: string;
      outcome?: string;
      /** Format: uuid */
      revisionId?: string;
    };
    BulkRequest: {
      action: string;
      items: components['schemas']['BulkItem'][];
      note?: string;
      reasonCode: string;
    };
    BulkResult: {
      /** Format: uuid */
      batchId?: string;
      results?: components['schemas']['BulkItemResult'][];
    };
    Cancel: {
      /** Format: int64 */
      expectedVersion?: number;
      reason?: string;
    };
    Candidate: {
      /** Format: uuid */
      id?: string;
      note?: string;
      otherAddress?: string;
      otherAreaM2?: number;
      /** Format: uuid */
      otherAssetId?: string;
      /** Format: uuid */
      otherListingId?: string;
      /** Format: uuid */
      otherOwnerId?: string;
      /** Format: int64 */
      otherPriceVnd?: number;
      otherSlug?: string;
      otherStatus?: string;
      otherTitle?: string;
      reasons?: string[];
      score?: number;
      status?: string;
    };
    CatalogPublicProfileResponse: {
      amenities?: components['schemas']['Amenity'][];
      project?: components['schemas']['ProjectRow'];
    };
    ChallengeRequest: {
      challengeToken: string;
    };
    ChangePasswordRequest: {
      currentPassword: string;
      newPassword: string;
    };
    Claim: {
      /** Format: date-time */
      expiresAt?: string;
      mine?: boolean;
      /** Format: uuid */
      staffId?: string;
      staffName?: string;
    };
    ClaimView: {
      /** Format: date-time */
      expiresAt?: string;
      mine?: boolean;
      /** Format: uuid */
      moderatorId?: string;
      moderatorName?: string;
    };
    CodeRequest: {
      code: string;
    };
    CohortRow: {
      /** Format: int64 */
      devices?: number;
      retentionPercent?: number[];
      /** Format: date */
      week?: string;
    };
    Cohorts: {
      definition?: string;
      reason?: string;
      rows?: components['schemas']['CohortRow'][];
      /** @enum {string} */
      status?: 'MEASURED' | 'NOT_MEASURED';
    };
    Collection: {
      ingestionEnabled?: boolean;
      scope?: string;
      /** Format: int64 */
      webEvents?: number;
      /** Format: int64 */
      webSessions?: number;
    };
    Comparison: {
      documentValue?: string;
      field?: string;
      identityValue?: string;
      label?: string;
      result?: string;
    };
    Confirm: {
      /** Format: int64 */
      expectedVersion?: number;
      /** Format: uuid */
      slotId?: string;
    };
    ConsentReceipt: {
      /** Format: date-time */
      recordedAt?: string;
      /** Format: uuid */
      recordId?: string;
    };
    CreateArticleRequest: {
      authorName: string;
      canonicalUrl?: string;
      /** @enum {string} */
      category: 'LEGAL_POLICY' | 'KNOWLEDGE' | 'MARKET_INSIGHTS';
      contentHtml: string;
      coverImageUrl?: string;
      legalReference?: string;
      metaDescription?: string;
      slug: string;
      sourceName?: string;
      sourceUrl?: string;
      summary?: string;
      title: string;
    };
    CreateDepositRequest: {
      /** Format: uuid */
      buyerId?: string;
      buyerIdNumber: string;
      buyerName: string;
      buyerPhone: string;
      depositAmount: number;
      /** Format: uuid */
      listingId: string;
      termsConditions?: string;
    };
    CreateLeadRequest: {
      consentPolicy?: boolean;
      fullName: string;
      /** Format: uuid */
      listingId: string;
      note?: string;
      phone: string;
      /** @enum {string} */
      requestType?: 'VIEWING' | 'CONSULTATION';
    };
    CreateListingDraftRequest: {
      addressSummary?: string;
      areaM2: number;
      /** Format: int32 */
      bathrooms?: number;
      /** Format: int32 */
      bedrooms?: number;
      /** Format: int64 */
      depositVnd?: number;
      description?: string;
      direction?: string;
      districtCode?: string;
      /** Format: int32 */
      floors?: number;
      frontageM?: number;
      /** @enum {string} */
      furnishing?: 'NONE' | 'BASIC' | 'FULL';
      imageUrls?: string[];
      legalStatus?: string;
      /** @enum {string} */
      legalStatusCode?: 'RED_BOOK' | 'PINK_BOOK' | 'SALE_CONTRACT' | 'PENDING_CERTIFICATE' | 'OTHER';
      /** Format: int64 */
      monthlyServiceFeeVnd?: number;
      /** Format: int64 */
      priceVnd: number;
      /** Format: uuid */
      projectId?: string;
      /** @enum {string} */
      propertyType: 'APARTMENT' | 'HOUSE' | 'VILLA' | 'TOWNHOUSE' | 'LAND';
      provinceCode?: string;
      /** Format: double */
      publicLatitude?: number;
      /** Format: double */
      publicLongitude?: number;
      /** @enum {string} */
      purpose: 'SALE' | 'RENT';
      roadWidthM?: number;
      title: string;
      wardCode?: string;
    };
    CreateProjectRequest: {
      address: string;
      developerName: string;
      districtCode: string;
      /** Format: int32 */
      handoverYear?: number;
      legalLicenseNumber: string;
      name: string;
      provinceCode?: string;
      totalAreaM2?: number;
      /** Format: int32 */
      totalBlocks?: number;
      /** Format: int32 */
      totalUnits?: number;
    };
    CreateRequest: {
      alertBackOnMarket?: boolean;
      alertNew?: boolean;
      alertPriceDrop?: boolean;
      filter?: {
        [key: string]: string;
      };
      frequency?: string;
      name?: string;
    };
    Dashboard: {
      alerts?: components['schemas']['Alert'][];
      breakdowns?: {
        [key: string]: components['schemas']['Breakdown'][];
      };
      cohorts?: components['schemas']['Cohorts'];
      collection?: components['schemas']['Collection'];
      filters?: components['schemas']['Filters'];
      freshness?: components['schemas']['AnalyticsFreshness'];
      funnels?: components['schemas']['Funnel'][];
      /** Format: date-time */
      generatedAt?: string;
      metrics?: components['schemas']['NamedMetric'][];
      trend?: components['schemas']['TrendDay'][];
      webVitals?: components['schemas']['VitalRow'][];
      webVitalsReason?: string;
      window?: components['schemas']['WindowView'];
    };
    DecisionView: {
      /** Format: uuid */
      bulkBatchId?: string;
      /** Format: date-time */
      createdAt?: string;
      decision?: string;
      /** Format: uuid */
      id?: string;
      /** Format: uuid */
      moderatorId?: string;
      moderatorName?: string;
      note?: string;
      reasonCode?: string;
      /** Format: uuid */
      revisionId?: string;
      /** Format: int32 */
      revisionNumber?: number;
    };
    DepositActionRequest: {
      /** Format: uuid */
      operatorId?: string;
      reason?: string;
    };
    DepositContractResponse: {
      /** Format: uuid */
      buyerId?: string;
      buyerIdMasked?: string;
      buyerName?: string;
      buyerOtpVerified?: boolean;
      buyerPhone?: string;
      /** Format: date-time */
      buyerSignedAt?: string;
      /** Format: date-time */
      completedAt?: string;
      /** Format: date-time */
      createdAt?: string;
      depositAmount?: number;
      disputeReason?: string;
      /** Format: date-time */
      escrowLockedAt?: string;
      escrowTransactions?: components['schemas']['EscrowTransactionResponse'][];
      /** Format: uuid */
      id?: string;
      /** Format: uuid */
      listingId?: string;
      listingPrice?: number;
      /** Format: uuid */
      sellerId?: string;
      sellerName?: string;
      sellerOtpVerified?: boolean;
      sellerPhone?: string;
      /** Format: date-time */
      sellerSignedAt?: string;
      /** @enum {string} */
      status?: 'DRAFT' | 'AWAITING_SELLER_SIGN' | 'ESCROW_LOCKED' | 'COMPLETED' | 'REFUNDED' | 'DISPUTED';
      termsConditions?: string;
      /** Format: date-time */
      updatedAt?: string;
    };
    DismissReportRequest: {
      dismissNote: string;
      resumeListing?: boolean;
    };
    DistrictMetric: {
      districtName?: string;
      /** Format: int64 */
      leadsCount?: number;
      /** Format: int64 */
      listingsCount?: number;
    };
    DocumentAccess: {
      /** Format: date-time */
      expiresAt?: string;
      identity?: components['schemas']['Documents'];
      ownershipDocumentUrls?: string[];
      token?: string;
    };
    DocumentAccessRequest: {
      password: string;
    };
    Documents: {
      idCardBackUrl?: string;
      idCardFrontUrl?: string;
      selfieUrl?: string;
    };
    DraftView: {
      addressSummary?: string;
      areaM2?: number;
      /** Format: int32 */
      bathrooms?: number;
      /** Format: int32 */
      bedrooms?: number;
      /** Format: int64 */
      depositVnd?: number;
      description?: string;
      direction?: string;
      districtCode?: string;
      /** Format: int32 */
      floors?: number;
      frontageM?: number;
      furnishing?: string;
      hasPublicVersion?: boolean;
      imageUrls?: string[];
      legalStatus?: string;
      legalStatusCode?: string;
      /** Format: uuid */
      listingId?: string;
      listingStatus?: string;
      /** Format: int64 */
      monthlyServiceFeeVnd?: number;
      /** Format: int64 */
      priceVnd?: number;
      /** Format: uuid */
      projectId?: string;
      propertyType?: string;
      provinceCode?: string;
      /** Format: double */
      publicLatitude?: number;
      /** Format: double */
      publicLongitude?: number;
      purpose?: string;
      quality?: components['schemas']['Report'];
      rejectionReason?: string;
      /** Format: uuid */
      revisionId?: string;
      /** Format: int32 */
      revisionNumber?: number;
      revisionStatus?: string;
      roadWidthM?: number;
      slug?: string;
      title?: string;
      /** Format: int64 */
      version?: number;
      wardCode?: string;
    };
    DuplicateDecisionRequest: {
      note?: string;
      status: string;
    };
    EngagementItem: {
      /** Format: date-time */
      addedAt?: string;
      addedByMe?: boolean;
      listing?: components['schemas']['ListingSummaryV2'];
      /** Format: uuid */
      listingId?: string;
      unavailable?: components['schemas']['Unavailable'];
    };
    EngagementTokenRequest: {
      token?: string;
    };
    Enrollment: {
      /** Format: date-time */
      challengeExpiresAt?: string;
      otpauthUri?: string;
      secret?: string;
    };
    EnrollmentCompleted: {
      recoveryCodes?: string[];
      session?: components['schemas']['AuthResult'];
    };
    EscrowTransactionResponse: {
      /** @enum {string} */
      action?: 'DEPOSIT' | 'LOCK' | 'RELEASE' | 'REFUND' | 'DISPUTE';
      amount?: number;
      /** Format: date-time */
      createdAt?: string;
      /** Format: uuid */
      id?: string;
      note?: string;
      /** Format: uuid */
      performedBy?: string;
    };
    EstimatePriceRequest: {
      areaM2: number;
      districtCode?: string;
      propertyType: string;
      provinceCode?: string;
    };
    EstimatePriceResponse: {
      /** Format: int32 */
      confidenceScorePercent?: number;
      marketTrendNote?: string;
      maxPriceVnd?: number;
      minPriceVnd?: number;
      suggestedUnitPriceVndPerM2?: number;
    };
    Evidence: {
      certificateNumber?: string;
      comparisons?: components['schemas']['Comparison'][];
      districtCode?: string;
      documentUrls?: string[];
      /** Format: date-time */
      expiresAt?: string;
      history?: components['schemas']['VerificationDecision'][];
      /** Format: uuid */
      id?: string;
      identity?: components['schemas']['Identity'];
      listingAddress?: string;
      /** Format: uuid */
      listingId?: string;
      /** Format: uuid */
      listingOwnerId?: string;
      listingSlug?: string;
      listingStatus?: string;
      listingTitle?: string;
      ownerNameOnDoc?: string;
      /** Format: date-time */
      revokedAt?: string;
      status?: string;
      /** Format: date-time */
      submittedAt?: string;
      verificationType?: string;
      verifierNote?: string;
    };
    Facts: {
      /** Format: int32 */
      bathrooms?: number;
      /** Format: int32 */
      bedrooms?: number;
      direction?: string;
      /** Format: int32 */
      floors?: number;
      frontageM?: number;
      legalStatusText?: string;
      roadWidthM?: number;
    };
    Feed: {
      items?: components['schemas']['NotificationItem'][];
      /** Format: int64 */
      nextBefore?: number;
      /** Format: int64 */
      unreadCount?: number;
    };
    FieldDiff: {
      fieldLabel?: string;
      fieldName?: string;
      isChanged?: boolean;
      newValue?: string;
      oldValue?: string;
    };
    Filters: {
      area?: string;
      device?: string;
      source?: string;
    };
    ForgotPasswordRequest: {
      /** Format: email */
      email: string;
    };
    Funnel: {
      definition?: string;
      key?: string;
      source?: string;
      steps?: components['schemas']['FunnelStep'][];
      title?: string;
    };
    FunnelAnalyticsResponse: {
      /** Format: int64 */
      contactedCount?: number;
      /** Format: double */
      conversionRatePercent?: number;
      /** Format: int64 */
      dealsClosed?: number;
      /** Format: int64 */
      detailViews?: number;
      /** Format: int64 */
      impressions?: number;
      /** Format: int64 */
      leadsSubmitted?: number;
      steps?: components['schemas']['StepMetric'][];
    };
    FunnelStep: {
      count?: components['schemas']['Metric'];
      fromPrevious?: components['schemas']['Metric'];
      key?: string;
      label?: string;
    };
    Home: {
      areas?: components['schemas']['AreaCard'][];
      /** Format: date-time */
      dataAsOf?: string;
      projects?: components['schemas']['ProjectCard'][];
    };
    IamEvent: {
      byAdmin?: boolean;
      device?: string;
      /** Format: uuid */
      id?: string;
      ipHint?: string;
      /** Format: date-time */
      occurredAt?: string;
      type?: string;
    };
    IamStatus: {
      enrolled?: boolean;
      /** Format: date-time */
      enrolledAt?: string;
      /** Format: int32 */
      recoveryCodesRemaining?: number;
      required?: boolean;
    };
    IamTokenRequest: {
      token: string;
    };
    Identity: {
      /** Format: date-time */
      expiresAt?: string;
      fullName?: string;
      /** Format: uuid */
      kycId?: string;
      status?: string;
      /** Format: date-time */
      verifiedAt?: string;
    };
    ImageDto: {
      /** Format: int32 */
      height?: number;
      placeholder?: components['schemas']['Placeholder'];
      srcset?: components['schemas']['Source'][];
      url?: string;
      /** Format: int32 */
      width?: number;
    };
    ImportReport: {
      /** Format: uuid */
      batchId?: string;
      committed?: boolean;
      dryRun?: boolean;
      duplicate?: boolean;
      fileErrors?: components['schemas']['Issue'][];
      fileSha256?: string;
      /** Format: int32 */
      quotaRemaining?: number;
      rows?: components['schemas']['RowResult'][];
      /** Format: int32 */
      totalRows?: number;
      /** Format: int32 */
      validRows?: number;
    };
    IndexInfo: {
      /** Format: date-time */
      activatedAt?: string;
      /** Format: date-time */
      backfillCompletedAt?: string;
      /** Format: int64 */
      backfilledRows?: number;
      /** Format: date-time */
      createdAt?: string;
      /** Format: int64 */
      documents?: number;
      /** Format: int32 */
      mappingVersion?: number;
      name?: string;
      /** Format: date-time */
      retiredAt?: string;
      role?: string;
    };
    Info: {
      address?: string;
      businessRegistration?: string;
      complete?: boolean;
      email?: string;
      hotlineHours?: string;
      legalName?: string;
      phone?: string;
      representative?: string;
      siteName?: string;
      taxCode?: string;
    };
    IngestionResult: {
      /** Format: int32 */
      accepted?: number;
      /** Format: int32 */
      dropped?: number;
      /** Format: int32 */
      duplicates?: number;
    };
    Inventory: {
      /** Format: date-time */
      dataAsOf?: string;
      /** Format: date-time */
      lastListingUpdate?: string;
      statistics?: components['schemas']['Statistic'][];
      /** Format: int64 */
      total?: number;
      types?: components['schemas']['TypeCount'][];
    };
    Issue: {
      field?: string;
      message?: string;
    };
    KycAccessRequest: {
      password?: string;
      reason?: string;
    };
    KycDocumentAccess: {
      /** Format: date-time */
      expiresAt?: string;
      token?: string;
    };
    KycDocumentsResponse: {
      idCardBackUrl?: string;
      idCardFrontUrl?: string;
      selfieUrl?: string;
    };
    LeadEvent: {
      /** Format: uuid */
      actorId?: string;
      actorName?: string;
      /** Format: date-time */
      createdAt?: string;
      data?: string;
      /** Format: uuid */
      id?: string;
      note?: string;
      type?: string;
    };
    LeadInquiryItem: {
      /** Format: date-time */
      createdAt?: string;
      /** Format: date-time */
      firstResponseAt?: string;
      /** Format: uuid */
      id?: string;
      listingAddress?: string;
      listingAvailable?: boolean;
      /** Format: uuid */
      listingId?: string;
      listingImageUrl?: string;
      listingSlug?: string;
      listingTitle?: string;
      note?: string;
      openAppointment?: components['schemas']['AppointmentSummary'];
      /** @enum {string} */
      requestType?: 'VIEWING' | 'CONSULTATION';
      /** @enum {string} */
      status?: 'NEW' | 'CONTACTED' | 'APPOINTED' | 'CLOSED' | 'SPAM' | 'WITHDRAWN';
      /** Format: date-time */
      updatedAt?: string;
      /** Format: int64 */
      version?: number;
      /** Format: date-time */
      withdrawnAt?: string;
    };
    LeadInquiryItem2: {
      items?: components['schemas']['LeadInquiryItem'][];
      /** Format: int32 */
      page?: number;
      /** Format: int32 */
      size?: number;
      statusCounts?: {
        [key: string]: number;
      };
      /** Format: int64 */
      totalElements?: number;
      /** Format: int32 */
      totalPages?: number;
    };
    LeadItem: {
      /** Format: int64 */
      activeLeads?: number;
      address?: string;
      /** Format: int64 */
      closedLeads?: number;
      imageUrl?: string;
      /** Format: date-time */
      lastLeadAt?: string;
      /** Format: uuid */
      listingId?: string;
      /** Format: int64 */
      newLeads?: number;
      slug?: string;
      title?: string;
      /** Format: int64 */
      totalLeads?: number;
    };
    LeadLeadItem: {
      /** Format: uuid */
      assigneeId?: string;
      assigneeName?: string;
      consentPolicy?: boolean;
      /** Format: date-time */
      createdAt?: string;
      /** Format: date-time */
      firstResponseAt?: string;
      fullName?: string;
      /** Format: uuid */
      id?: string;
      listingAddress?: string;
      /** Format: uuid */
      listingId?: string;
      listingImageUrl?: string;
      listingSlug?: string;
      listingTitle?: string;
      maskedPhone?: string;
      note?: string;
      openAppointment?: components['schemas']['AppointmentSummary'];
      overdue?: boolean;
      qualification?: string;
      qualificationNote?: string;
      qualificationReason?: string;
      /** @enum {string} */
      requestType?: 'VIEWING' | 'CONSULTATION';
      /** Format: date-time */
      responseDueAt?: string;
      /** @enum {string} */
      status?: 'NEW' | 'CONTACTED' | 'APPOINTED' | 'CLOSED' | 'SPAM' | 'WITHDRAWN';
      /** Format: date-time */
      updatedAt?: string;
      /** Format: int64 */
      version?: number;
      /** Format: date-time */
      withdrawnAt?: string;
    };
    LeadLeadItem2: {
      items?: components['schemas']['LeadLeadItem'][];
      /** Format: int32 */
      page?: number;
      /** Format: int32 */
      size?: number;
      statusCounts?: {
        [key: string]: number;
      };
      /** Format: int64 */
      totalElements?: number;
      /** Format: int32 */
      totalPages?: number;
    };
    LeadListingPageResponse: {
      items?: components['schemas']['LeadItem'][];
      /** Format: int32 */
      page?: number;
      /** Format: int32 */
      size?: number;
      /** Format: int64 */
      totalElements?: number;
      /** Format: int32 */
      totalPages?: number;
    };
    LeadQueueItem: {
      caseNumber?: string;
      category?: string;
      claim?: components['schemas']['Claim'];
      /** Format: date-time */
      createdAt?: string;
      description?: string;
      /** Format: uuid */
      id?: string;
      /** Format: uuid */
      listingId?: string;
      listingSlug?: string;
      listingStatus?: string;
      listingTitle?: string;
      /** Format: int64 */
      minutesToDue?: number;
      /** Format: uuid */
      ownerId?: string;
      ownerOutcome?: string;
      reporterPhoneMasked?: string;
      resolutionNote?: string;
      /** Format: date-time */
      resolvedAt?: string;
      severity?: string;
      slaBreached?: boolean;
      /** Format: date-time */
      slaDueAt?: string;
      status?: string;
    };
    LeadQueuePage: {
      items?: components['schemas']['LeadQueueItem'][];
      /** Format: int32 */
      page?: number;
      /** Format: int32 */
      size?: number;
      /** Format: int64 */
      total?: number;
    };
    LeadSlot: {
      /** Format: date-time */
      endsAt?: string;
      /** Format: date-time */
      startsAt?: string;
    };
    LeadSlot2: {
      /** Format: date-time */
      endsAt?: string;
      /** Format: uuid */
      id?: string;
      /** Format: date-time */
      startsAt?: string;
    };
    ListingCheck: {
      /** Format: date-time */
      checkedAt?: string;
      status?: string;
    };
    ListingDetailResponse: {
      addressSummary?: string;
      areaM2?: number;
      /** Format: int32 */
      bathrooms?: number;
      /** Format: int32 */
      bedrooms?: number;
      /** Format: date-time */
      createdAt?: string;
      description?: string;
      direction?: string;
      districtCode?: string;
      /** Format: int32 */
      floors?: number;
      frontageM?: number;
      /** Format: uuid */
      id?: string;
      imageUrls?: string[];
      isShowcase?: boolean;
      isVerified?: boolean;
      legalStatus?: string;
      /** Format: uuid */
      ownerId?: string;
      pricePeriod?: string;
      /** Format: int64 */
      priceVnd?: number;
      propertyType?: string;
      provinceCode?: string;
      /** Format: double */
      publicLatitude?: number;
      /** Format: double */
      publicLongitude?: number;
      purpose?: string;
      /** Format: int32 */
      revisionNumber?: number;
      revisionStatus?: string;
      roadWidthM?: number;
      slug?: string;
      status?: string;
      title?: string;
      /** Format: date-time */
      updatedAt?: string;
      wardCode?: string;
    };
    ListingDetailV2: {
      areaM2?: number;
      /** Format: int32 */
      bathrooms?: number;
      /** Format: int32 */
      bedrooms?: number;
      description?: string;
      facts?: components['schemas']['Facts'];
      freshness?: components['schemas']['SearchFreshness'];
      furnishing?: string;
      /** Format: uuid */
      id?: string;
      image?: components['schemas']['ImageDto'];
      /** Format: int32 */
      imageCount?: number;
      images?: components['schemas']['ImageDto'][];
      legal?: components['schemas']['SearchLegal'];
      location?: components['schemas']['SearchLocation'];
      price?: components['schemas']['SearchMoney'];
      priceChange?: components['schemas']['PriceChange'];
      project?: components['schemas']['Project'];
      propertyType?: string;
      purpose?: string;
      rentTerms?: components['schemas']['SearchRentTerms'];
      /** Format: int32 */
      revisionNumber?: number;
      seller?: components['schemas']['Seller'];
      slug?: string;
      title?: string;
      trust?: components['schemas']['Trust'];
      unitPrice?: components['schemas']['SearchUnitPrice'];
    };
    ListingDiffResponse: {
      /** Format: int32 */
      changedCount?: number;
      /** Format: uuid */
      currentRevisionId?: string;
      /** Format: int32 */
      currentRevisionNumber?: number;
      diffs?: components['schemas']['FieldDiff'][];
      isFirstSubmission?: boolean;
      /** Format: uuid */
      listingId?: string;
      /** Format: uuid */
      previousRevisionId?: string;
      /** Format: int32 */
      previousRevisionNumber?: number;
    };
    ListingFreshness: {
      /** Format: date-time */
      availabilityConfirmedAt?: string;
      /** Format: int64 */
      daysUntilExpiry?: number;
      /** Format: date-time */
      expiresAt?: string;
      expiringSoon?: boolean;
      renewable?: boolean;
      /** Format: date-time */
      soldCheckDueAt?: string;
    };
    ListingItem: {
      code?: string;
      hint?: string;
      label?: string;
      /** @enum {string} */
      status?: 'PASS' | 'WARN' | 'NO_DATA';
    };
    ListingLegal: {
      code?: string;
      detail?: string;
      label?: string;
    };
    ListingLocation: {
      addressSummary?: string;
      districtCode?: string;
      /** Format: double */
      lat?: number;
      /** Format: double */
      lng?: number;
      precision?: string;
      provinceCode?: string;
      wardCode?: string;
    };
    ListingMoney: {
      /** Format: int64 */
      amount?: number;
      currency?: string;
      period?: string;
    };
    ListingPage: {
      items?: components['schemas']['ListingRow'][];
      /** Format: int32 */
      page?: number;
      /** Format: int32 */
      size?: number;
      /** Format: int64 */
      total?: number;
    };
    ListingPublicProfileResponse: {
      /** Format: int64 */
      activeListingCount?: number;
      avatarMediaUrl?: string;
      displayName?: string;
      identityVerified?: boolean;
      /** Format: date-time */
      memberSince?: string;
    };
    ListingRentTerms: {
      /** Format: int64 */
      deposit?: number;
      /** Format: int64 */
      monthlyServiceFee?: number;
    };
    ListingReportResponse: {
      caseNumber?: string;
      /** @enum {string} */
      category?: 'SCAM_DEPOSIT' | 'FAKE_SOLD' | 'INCORRECT_PRICE' | 'OTHER';
      /** Format: date-time */
      createdAt?: string;
      description?: string;
      evidenceUrls?: string;
      /** Format: uuid */
      id?: string;
      /** Format: uuid */
      listingId?: string;
      reporterPhone?: string;
      reporterType?: string;
      resolutionNote?: string;
      /** Format: date-time */
      resolvedAt?: string;
      /** @enum {string} */
      severity?: 'P0_EMERGENCY' | 'HIGH' | 'MEDIUM' | 'LOW';
      /** @enum {string} */
      status?: 'PENDING' | 'WAITING_REPLY' | 'RESOLVED' | 'DISMISSED' | 'APPEALED';
    };
    ListingRow: {
      addressSummary?: string;
      areaM2?: number;
      /** Format: date-time */
      createdAt?: string;
      districtCode?: string;
      /** Format: date-time */
      expiresAt?: string;
      hasPendingEdit?: boolean;
      hasPublicRevision?: boolean;
      /** Format: uuid */
      id?: string;
      /** Format: int32 */
      latestRevisionNumber?: number;
      /** Format: uuid */
      ownerId?: string;
      ownerName?: string;
      /** Format: int64 */
      priceVnd?: number;
      /** Format: uuid */
      propertyAssetId?: string;
      propertyType?: string;
      purpose?: string;
      slug?: string;
      source?: string;
      status?: string;
      title?: string;
      /** Format: date-time */
      updatedAt?: string;
    };
    ListingSummaryResponse: {
      addressSummary?: string;
      areaM2?: number;
      /** Format: uuid */
      id?: string;
      isShowcase?: boolean;
      isVerified?: boolean;
      pricePeriod?: string;
      /** Format: int64 */
      priceVnd?: number;
      primaryImageUrl?: string;
      propertyType?: string;
      /** Format: double */
      publicLatitude?: number;
      /** Format: double */
      publicLongitude?: number;
      /** Format: date-time */
      publishedAt?: string;
      purpose?: string;
      sellerAvatarUrl?: string;
      /** Format: uuid */
      sellerId?: string;
      sellerName?: string;
      slug?: string;
      title?: string;
    };
    ListingSummaryV2: {
      areaM2?: number;
      /** Format: int32 */
      bathrooms?: number;
      /** Format: int32 */
      bedrooms?: number;
      freshness?: components['schemas']['SearchFreshness'];
      /** Format: uuid */
      id?: string;
      image?: components['schemas']['ImageDto'];
      /** Format: int32 */
      imageCount?: number;
      location?: components['schemas']['SearchLocation'];
      price?: components['schemas']['SearchMoney'];
      priceChange?: components['schemas']['PriceChange'];
      project?: components['schemas']['Project'];
      propertyType?: string;
      purpose?: string;
      seller?: components['schemas']['Seller'];
      slug?: string;
      title?: string;
      trust?: components['schemas']['Trust'];
      unitPrice?: components['schemas']['SearchUnitPrice'];
    };
    ListingUnitPrice: {
      /** Format: int64 */
      amount?: number;
      per?: string;
    };
    ListingVerificationResponse: {
      certificateNumber?: string;
      /** Format: date-time */
      createdAt?: string;
      decidedByName?: string;
      decisionReasonCode?: string;
      documentUrls?: string;
      /** Format: date-time */
      expiresAt?: string;
      /** Format: uuid */
      id?: string;
      listingAddress?: string;
      /** Format: uuid */
      listingId?: string;
      listingTitle?: string;
      ownerNameOnDoc?: string;
      /** Format: date-time */
      revokedAt?: string;
      /** @enum {string} */
      status?: 'PENDING' | 'VERIFIED_OWNER' | 'REJECTED' | 'REVOKED';
      userKyc?: components['schemas']['UserKycResponse'];
      /** Format: uuid */
      userKycId?: string;
      /** @enum {string} */
      verificationType?: 'CERTIFICATE_OF_OWNERSHIP' | 'POWER_OF_ATTORNEY' | 'PROJECT_PURCHASE_CONTRACT';
      /** Format: date-time */
      verifiedAt?: string;
      verifierNote?: string;
    };
    LoginRequest: {
      /** Format: email */
      email: string;
      password: string;
    };
    MapClusterDto: {
      bbox?: number[];
      /** Format: int64 */
      count?: number;
      /** Format: double */
      lat?: number;
      /** Format: double */
      lng?: number;
    };
    MapPointDto: {
      /** Format: uuid */
      id?: string;
      /** Format: double */
      lat?: number;
      /** Format: double */
      lng?: number;
      price?: components['schemas']['SearchMoney'];
      propertyType?: string;
      slug?: string;
    };
    MapResponseV2: {
      clusters?: components['schemas']['MapClusterDto'][];
      /** Format: date-time */
      dataAsOf?: string;
      engine?: string;
      mode?: string;
      points?: components['schemas']['MapPointDto'][];
      total?: components['schemas']['TotalDto'];
    };
    Member: {
      /** Format: date-time */
      joinedAt?: string;
      muted?: boolean;
      name?: string;
      role?: string;
      /** Format: uuid */
      userId?: string;
    };
    Metric: {
      /** Format: int64 */
      denominator?: number;
      /** Format: int64 */
      numerator?: number;
      reason?: string;
      /** @enum {string} */
      status?: 'MEASURED' | 'NOT_MEASURED';
      unit?: string;
      /** Format: double */
      value?: number;
    };
    MfaConfirmRequest: {
      challengeToken: string;
      code: string;
    };
    MfaVerifyRequest: {
      challengeToken: string;
      code?: string;
      recoveryCode?: string;
    };
    ModerationDecision: {
      decision?: string;
      /** Format: uuid */
      id?: string;
      /** Format: uuid */
      listingId?: string;
      /** Format: uuid */
      moderatorId?: string;
      /** Format: uuid */
      publicRevisionId?: string;
      reasonCode?: string;
      /** Format: uuid */
      revisionId?: string;
      status?: string;
      success?: boolean;
    };
    ModerationQueueItem: {
      addressSummary?: string;
      /** Format: int64 */
      ageMinutes?: number;
      areaM2?: number;
      claim?: components['schemas']['ClaimView'];
      districtCode?: string;
      kind?: string;
      /** Format: uuid */
      listingId?: string;
      listingStatus?: string;
      /** Format: int32 */
      mediaCount?: number;
      /** Format: int32 */
      openDuplicates?: number;
      /** Format: uuid */
      ownerId?: string;
      ownerName?: string;
      /** Format: int64 */
      priceVnd?: number;
      propertyType?: string;
      purpose?: string;
      /** Format: uuid */
      revisionId?: string;
      /** Format: int32 */
      revisionNumber?: number;
      slaBreached?: boolean;
      /** Format: date-time */
      slaDueAt?: string;
      /** Format: date-time */
      submittedAt?: string;
      title?: string;
    };
    ModerationQueuePage: {
      items?: components['schemas']['ModerationQueueItem'][];
      /** Format: int32 */
      page?: number;
      /** Format: int32 */
      size?: number;
      stats?: components['schemas']['QueueStats'];
      /** Format: int64 */
      total?: number;
    };
    MuteRequest: {
      muted?: boolean;
    };
    MyKycStatus: {
      canSubmit?: boolean;
      /** Format: date-time */
      decidedAt?: string;
      /** Format: date-time */
      expiresAt?: string;
      rejectionReason?: string;
      /** Format: date-time */
      revokedAt?: string;
      status?: string;
      /** Format: date-time */
      submittedAt?: string;
      timeline?: components['schemas']['VerificationDecision'][];
    };
    MyListingItem: {
      /** Format: date-time */
      createdAt?: string;
      freshness?: components['schemas']['ListingFreshness'];
      /** Format: uuid */
      id?: string;
      /** Format: int64 */
      leadCount?: number;
      pendingEdit?: components['schemas']['VersionSummary'];
      publicVersion?: components['schemas']['VersionSummary'];
      quality?: components['schemas']['Report'];
      slug?: string;
      source?: string;
      status?: string;
      thumbnailUrl?: string;
      /** Format: date-time */
      updatedAt?: string;
      /** Format: int64 */
      version?: number;
    };
    MyListingsPage: {
      counts?: {
        [key: string]: number;
      };
      items?: components['schemas']['MyListingItem'][];
      /** Format: int32 */
      page?: number;
      /** Format: int32 */
      size?: number;
      /** Format: int64 */
      total?: number;
      /** Format: int32 */
      totalPages?: number;
    };
    NamedMetric: {
      definition?: string;
      group?: string;
      key?: string;
      label?: string;
      metric?: components['schemas']['Metric'];
      source?: string;
    };
    NameRequest: {
      name?: string;
    };
    NotificationItem: {
      category?: string;
      /** Format: date-time */
      createdAt?: string;
      /** Format: uuid */
      id?: string;
      link?: string;
      message?: string;
      /** Format: date-time */
      readAt?: string;
      /** Format: int64 */
      seq?: number;
      title?: string;
      type?: string;
    };
    NotificationView: {
      /** Format: date-time */
      createdAt?: string;
      /** Format: uuid */
      id?: string;
      message?: string;
      /** Format: date-time */
      readAt?: string;
      title?: string;
      type?: string;
    };
    Order: {
      accountNameSnapshot?: string;
      accountNumberSnapshot?: string;
      /** Format: int64 */
      amountVnd?: number;
      bankBinSnapshot?: string;
      /** Format: date-time */
      createdAt?: string;
      /** Format: int32 */
      durationDays?: number;
      exceptionReason?: string;
      /** Format: uuid */
      id?: string;
      invoiceNumber?: string;
      planCode?: string;
      planName?: string;
      qrUrl?: string;
      /** Format: int32 */
      quota?: number;
      reference?: string;
      /** Format: date-time */
      reportedAt?: string;
      resolution?: string;
      /** Format: date-time */
      reviewedAt?: string;
      reviewNote?: string;
      status?: string;
      /** Format: date-time */
      updatedAt?: string;
      /** Format: uuid */
      userId?: string;
    };
    OrderDetail: {
      events?: components['schemas']['OrderEvent'][];
      order?: components['schemas']['Order'];
    };
    OrderEvent: {
      /** Format: uuid */
      actorId?: string;
      actorName?: string;
      /** Format: date-time */
      createdAt?: string;
      data?: string;
      fromStatus?: string;
      /** Format: uuid */
      id?: string;
      note?: string;
      toStatus?: string;
      type?: string;
    };
    OrderPage: {
      items?: components['schemas']['Order'][];
      /** Format: int32 */
      page?: number;
      /** Format: int32 */
      size?: number;
      /** Format: int64 */
      total?: number;
    };
    Outcome: {
      /** Format: int64 */
      expectedVersion?: number;
      noShowParty?: string;
      note?: string;
      outcome?: string;
    };
    OwnershipCheck: {
      /** Format: date-time */
      checkedAt?: string;
      documentType?: string;
      /** Format: date-time */
      expiresAt?: string;
      status?: string;
    };
    PageInfo: {
      hasNext?: boolean;
      nextCursor?: string;
      /** Format: int32 */
      size?: number;
    };
    Placeholder: {
      dominantColor?: string;
      lqip?: string;
    };
    Plan: {
      code?: string;
      description?: string;
      /** Format: int32 */
      durationDays?: number;
      name?: string;
      /** Format: int64 */
      priceVnd?: number;
      /** Format: int32 */
      quota?: number;
    };
    PlanRequest: {
      planCode: string;
    };
    PolicyLink: {
      path?: string;
      /** Format: date-time */
      publishedAt?: string;
      summary?: string;
      title?: string;
    };
    PreferenceDto: {
      category?: string;
      email?: boolean;
      inApp?: boolean;
      mandatoryInApp?: boolean;
    };
    Preview: {
      addressSummary?: string;
      areaM2?: number;
      /** Format: int32 */
      bathrooms?: number;
      /** Format: int32 */
      bedrooms?: number;
      description?: string;
      districtCode?: string;
      /** Format: int32 */
      floors?: number;
      isPublic?: boolean;
      legalStatus?: string;
      /** Format: uuid */
      listingId?: string;
      listingStatus?: string;
      mediaUrls?: string[];
      /** Format: date-time */
      moderatedAt?: string;
      moderationNote?: string;
      /** Format: int64 */
      priceVnd?: number;
      propertyType?: string;
      purpose?: string;
      /** Format: uuid */
      revisionId?: string;
      /** Format: int32 */
      revisionNumber?: number;
      revisionStatus?: string;
      slug?: string;
      /** Format: date-time */
      submittedAt?: string;
      title?: string;
    };
    PreviewLinkResponse: {
      /** Format: date-time */
      expiresAt?: string;
      path?: string;
      token?: string;
    };
    PreviewView: {
      areaM2?: number;
      /** Format: int32 */
      bathrooms?: number;
      /** Format: int32 */
      bedrooms?: number;
      description?: string;
      direction?: string;
      /** Format: int32 */
      floors?: number;
      frontageM?: number;
      furnishing?: string;
      /** Format: uuid */
      id?: string;
      images?: components['schemas']['ImageDto'][];
      legal?: components['schemas']['ListingLegal'];
      location?: components['schemas']['ListingLocation'];
      previewOf?: string;
      price?: components['schemas']['ListingMoney'];
      propertyType?: string;
      purpose?: string;
      quality?: components['schemas']['Report'];
      rentTerms?: components['schemas']['ListingRentTerms'];
      /** Format: int32 */
      revisionNumber?: number;
      revisionStatus?: string;
      roadWidthM?: number;
      slug?: string;
      title?: string;
      unitPrice?: components['schemas']['ListingUnitPrice'];
    };
    PriceChange: {
      /** Format: date-time */
      changedAt?: string;
      direction?: string;
      /** Format: int64 */
      previousAmount?: number;
    };
    PriceHistoryResponse: {
      /** Format: uuid */
      listingId?: string;
      points?: components['schemas']['PricePointDto'][];
      purpose?: string;
    };
    PricePointDto: {
      /** Format: date-time */
      changedAt?: string;
      price?: components['schemas']['SearchMoney'];
    };
    ProblemDetails: {
      code?: string;
      detail?: string;
      errors?: components['schemas']['ValidationErrorItem'][];
      instance?: string;
      /** Format: int32 */
      status?: number;
      title?: string;
      traceId?: string;
      /** Format: uri */
      type?: string;
    };
    ProductAnalyticsOverviewResponse: {
      /** Format: int64 */
      activeListingsCount?: number;
      /** Format: double */
      avgModerationHours?: number;
      districtBreakdown?: components['schemas']['DistrictMetric'][];
      sourceBreakdownPercent?: {
        [key: string]: number;
      };
      /** Format: int64 */
      totalContactClicks?: number;
      /** Format: int64 */
      totalDetailViews?: number;
      /** Format: int64 */
      totalEscrowDeposited?: number;
      /** Format: int64 */
      totalLeadsSubmitted?: number;
      /** Format: double */
      verifiedOwnerRatioPercent?: number;
    };
    ProfileInput: {
      amenities?: components['schemas']['AmenityInput'][];
      description?: string;
      /** Format: date */
      infoCheckedAt?: string;
      infoSource?: string;
      status?: string;
      websiteUrl?: string;
    };
    Project: {
      /** Format: uuid */
      id?: string;
      name?: string;
      slug?: string;
    };
    ProjectCard: {
      /** Format: int64 */
      activeListings?: number;
      areaSlug?: string;
      districtName?: string;
      name?: string;
      slug?: string;
      status?: string;
    };
    ProjectList: {
      items?: components['schemas']['ProjectCard'][];
      /** Format: int32 */
      page?: number;
      /** Format: int32 */
      size?: number;
      /** Format: int64 */
      total?: number;
    };
    ProjectPage: {
      amenities?: components['schemas']['Amenity'][];
      inventory?: components['schemas']['Inventory'];
      project?: components['schemas']['ProjectRow'];
    };
    ProjectResponse: {
      address?: string;
      /** Format: date-time */
      createdAt?: string;
      developerName?: string;
      districtCode?: string;
      /** Format: int32 */
      handoverYear?: number;
      /** Format: uuid */
      id?: string;
      legalLicenseNumber?: string;
      name?: string;
      provinceCode?: string;
      slug?: string;
      /** @enum {string} */
      status?: 'ACTIVE' | 'PLANNING' | 'UNDER_CONSTRUCTION' | 'COMPLETED' | 'LOCKED';
      totalAreaM2?: number;
      /** Format: int32 */
      totalBlocks?: number;
      /** Format: int32 */
      totalUnits?: number;
      /** Format: date-time */
      updatedAt?: string;
    };
    ProjectRow: {
      address?: string;
      areaSlug?: string;
      description?: string;
      developerName?: string;
      districtCode?: string;
      districtName?: string;
      /** Format: int32 */
      handoverYear?: number;
      /** Format: uuid */
      id?: string;
      /** Format: date */
      infoCheckedAt?: string;
      infoSource?: string;
      legalLicenseNumber?: string;
      name?: string;
      provinceCode?: string;
      slug?: string;
      status?: string;
      totalAreaM2?: number;
      /** Format: int32 */
      totalBlocks?: number;
      /** Format: int32 */
      totalUnits?: number;
      /** Format: date-time */
      updatedAt?: string;
      websiteUrl?: string;
    };
    Propose: {
      note?: string;
      /** Format: int64 */
      replacesVersion?: number;
      slots?: components['schemas']['LeadSlot'][];
    };
    PublicArticleResponse: {
      /** @enum {string} */
      category?: 'LEGAL_POLICY' | 'KNOWLEDGE' | 'MARKET_INSIGHTS';
      categoryLabel?: string;
      currentRevision?: components['schemas']['PublicRevision'];
      /** Format: date-time */
      firstPublishedAt?: string;
      /** Format: uuid */
      id?: string;
      path?: string;
      /** Format: date-time */
      publishedAt?: string;
      slug?: string;
      /** @enum {string} */
      status?: 'DRAFT' | 'SUBMITTED' | 'SCHEDULED' | 'PUBLISHED' | 'SUPERSEDED' | 'ARCHIVED' | 'REJECTED';
      /** Format: date-time */
      updatedAt?: string;
    };
    PublicListingCard: {
      addressSummary?: string;
      areaM2?: number;
      /** Format: uuid */
      id?: string;
      isVerified?: boolean;
      pricePeriod?: string;
      /** Format: int64 */
      priceVnd?: number;
      primaryImageUrl?: string;
      propertyType?: string;
      /** Format: date-time */
      publishedAt?: string;
      purpose?: string;
      slug?: string;
      title?: string;
    };
    PublicPageResponse: {
      hasNext?: boolean;
      items?: components['schemas']['PublicArticleResponse'][];
      /** Format: int32 */
      page?: number;
      /** Format: int32 */
      size?: number;
      /** Format: int64 */
      total?: number;
    };
    PublicRevision: {
      authorName?: string;
      contentHtml?: string;
      coverImageUrl?: string;
      /** Format: uuid */
      id?: string;
      legalReference?: string;
      metaDescription?: string;
      /** Format: date-time */
      reviewedAt?: string;
      /** Format: int32 */
      revisionNumber?: number;
      sourceName?: string;
      sourceUrl?: string;
      summary?: string;
      title?: string;
    };
    PublicShortlist: {
      items?: components['schemas']['ListingSummaryV2'][];
      name?: string;
      ownerGivenName?: string;
      role?: string;
    };
    Qualify: {
      /** Format: int64 */
      expectedVersion?: number;
      note?: string;
      qualification?: string;
      reason?: string;
    };
    QualityScoreRequest: {
      description?: string;
      hasLegalDocs?: boolean;
      imageUrls?: string[];
      /** Format: double */
      latitude?: number;
      /** Format: double */
      longitude?: number;
      title?: string;
    };
    QualityScoreResponse: {
      duplicateWarning?: string;
      passedCriteria?: string[];
      possibleDuplicate?: boolean;
      rating?: string;
      /** Format: int32 */
      score?: number;
      suggestions?: string[];
    };
    QueueStats: {
      /** Format: date-time */
      oldestSubmittedAt?: string;
      /** Format: int64 */
      slaBreached?: number;
      /** Format: int64 */
      total?: number;
    };
    ReadAllRequest: {
      /** Format: int64 */
      upToSeq?: number;
    };
    ReasonRequest: {
      reason?: string;
    };
    ReceiptRequest: {
      note?: string;
      /** Format: int64 */
      receivedAmountVnd: number;
      receivedReference?: string;
    };
    RecoveryCodes: {
      recoveryCodes?: string[];
    };
    RegisterRequest: {
      accountType?: string;
      /** Format: email */
      email: string;
      name: string;
      password: string;
      returnTo?: string;
    };
    RegistrationResult: {
      email?: string;
      requiresEmailVerification?: boolean;
    };
    RejectKycRequest: {
      reason: string;
      reasonCode?: string;
    };
    RejectListingRequest: {
      reasonCode: string;
      reasonDetail?: string;
      /** Format: uuid */
      revisionId: string;
    };
    RejectRequest: {
      reason: string;
    };
    RejectRevisionRequest: {
      reason: string;
    };
    RejectVerificationRequest: {
      reason: string;
      reasonCode?: string;
    };
    RenameRequest: {
      /** Format: int64 */
      expectedVersion?: number;
      name?: string;
    };
    Report: {
      items?: components['schemas']['ListingItem'][];
      /** Format: int32 */
      passed?: number;
      /** Format: int32 */
      total?: number;
    };
    ResendVerificationRequest: {
      /** Format: email */
      email: string;
    };
    ResetPasswordRequest: {
      password: string;
      token: string;
    };
    ResolveReportRequest: {
      permanentlyLockListing?: boolean;
      resolutionNote: string;
    };
    ResolveRequest: {
      note: string;
      resolution: string;
    };
    ResponseStats: {
      /** Format: double */
      medianFirstResponseMinutes?: number;
      /** Format: int64 */
      sampleSize?: number;
    };
    ReviewRequest: {
      note?: string;
    };
    RevisionRequest: {
      authorName?: string;
      canonicalUrl?: string;
      contentHtml?: string;
      coverImageUrl?: string;
      legalReference?: string;
      metaDescription?: string;
      sourceName?: string;
      sourceUrl?: string;
      summary?: string;
      title?: string;
    };
    RevisionResponse: {
      /** Format: uuid */
      articleId?: string;
      authorName?: string;
      canonicalUrl?: string;
      contentHtml?: string;
      coverImageUrl?: string;
      /** Format: date-time */
      createdAt?: string;
      /** Format: uuid */
      id?: string;
      legalReference?: string;
      metaDescription?: string;
      rejectionReason?: string;
      /** Format: date-time */
      reviewedAt?: string;
      reviewedBy?: string;
      /** Format: int32 */
      revisionNumber?: number;
      sourceName?: string;
      sourceUrl?: string;
      /** @enum {string} */
      status?: 'DRAFT' | 'SUBMITTED' | 'SCHEDULED' | 'PUBLISHED' | 'SUPERSEDED' | 'ARCHIVED' | 'REJECTED';
      /** Format: date-time */
      submittedAt?: string;
      summary?: string;
      title?: string;
    };
    RevisionRow: {
      areaM2?: number;
      /** Format: date-time */
      createdAt?: string;
      /** Format: uuid */
      id?: string;
      isPublic?: boolean;
      /** Format: int32 */
      mediaCount?: number;
      /** Format: date-time */
      moderatedAt?: string;
      moderationNote?: string;
      /** Format: int64 */
      priceVnd?: number;
      /** Format: int32 */
      revisionNumber?: number;
      status?: string;
      /** Format: date-time */
      submittedAt?: string;
      title?: string;
    };
    RevokedCount: {
      /** Format: int32 */
      revokedSessions?: number;
    };
    RoleChange: {
      fromRole?: string;
      toRole?: string;
      /** Format: uuid */
      userId?: string;
    };
    RowResult: {
      errors?: components['schemas']['Issue'][];
      /** Format: int32 */
      line?: number;
      /** Format: uuid */
      listingId?: string;
      status?: string;
      title?: string;
      warnings?: string[];
    };
    Sample: {
      addressSummary?: string;
      /** Format: date-time */
      approvedAt?: string;
      /** Format: uuid */
      id?: string;
      /** Format: uuid */
      listingId?: string;
      listingStatus?: string;
      note?: string;
      /** Format: uuid */
      originalModeratorId?: string;
      originalModeratorName?: string;
      originalReasonCode?: string;
      /** Format: int64 */
      priceVnd?: number;
      /** Format: date-time */
      reviewedAt?: string;
      reviewerName?: string;
      /** Format: uuid */
      revisionId?: string;
      /** Format: int32 */
      revisionNumber?: number;
      status?: string;
      title?: string;
      /** Format: date */
      weekStart?: string;
    };
    SamplePage: {
      items?: components['schemas']['Sample'][];
      /** Format: int32 */
      page?: number;
      /** Format: int32 */
      size?: number;
      /** Format: int64 */
      total?: number;
    };
    SavedIds: {
      ids?: string[];
      /** Format: int32 */
      limit?: number;
    };
    SavedListingItem: {
      listing?: components['schemas']['ListingSummaryV2'];
      /** Format: uuid */
      listingId?: string;
      /** Format: date-time */
      savedAt?: string;
      unavailable?: components['schemas']['Unavailable'];
    };
    SavedListingPage: {
      items?: components['schemas']['SavedListingItem'][];
      /** Format: int32 */
      limit?: number;
      nextCursor?: string;
      /** Format: int32 */
      total?: number;
    };
    SavedSearchDto: {
      alertBackOnMarket?: boolean;
      alertNew?: boolean;
      alertPriceDrop?: boolean;
      /** Format: date-time */
      createdAt?: string;
      filter?: {
        [key: string]: string;
      };
      filterHash?: string;
      frequency?: string;
      /** Format: uuid */
      id?: string;
      /** Format: date-time */
      lastDigestAt?: string;
      name?: string;
      /** Format: date-time */
      nextDigestAt?: string;
      paused?: boolean;
      /** Format: int32 */
      pendingMatches?: number;
      query?: string;
      /** Format: int64 */
      version?: number;
    };
    SavedSearchList: {
      items?: components['schemas']['SavedSearchDto'][];
      /** Format: int32 */
      limit?: number;
    };
    SavedState: {
      /** Format: uuid */
      listingId?: string;
      saved?: boolean;
      /** Format: date-time */
      savedAt?: string;
    };
    SearchFreshness: {
      /** Format: date-time */
      availabilityConfirmedAt?: string;
      /** Format: date-time */
      publishedAt?: string;
      /** Format: date-time */
      updatedAt?: string;
    };
    SearchLegal: {
      code?: string;
      label?: string;
    };
    SearchLocation: {
      addressSummary?: string;
      districtCode?: string;
      districtName?: string;
      /** Format: double */
      lat?: number;
      /** Format: double */
      lng?: number;
      precision?: string;
      wardName?: string;
    };
    SearchMoney: {
      /** Format: int64 */
      amount?: number;
      currency?: string;
      period?: string;
    };
    SearchRentTerms: {
      /** Format: int64 */
      deposit?: number;
      /** Format: int64 */
      monthlyServiceFee?: number;
    };
    SearchResponseV2: {
      /** Format: date-time */
      dataAsOf?: string;
      degraded?: boolean;
      engine?: string;
      items?: components['schemas']['ListingSummaryV2'][];
      notices?: string[];
      pageInfo?: components['schemas']['PageInfo'];
      queryVersion?: string;
      suggestions?: components['schemas']['SuggestionDto'][];
      total?: components['schemas']['TotalDto'];
    };
    SearchStatus: {
      alias?: string;
      aliasTargets?: string[];
      enabled?: boolean;
      indices?: components['schemas']['IndexInfo'][];
      /** Format: double */
      oldestPendingJobSeconds?: number;
      /** Format: int64 */
      pendingJobs?: number;
      /** Format: int64 */
      readModelRows?: number;
      ready?: boolean;
    };
    SearchUnitPrice: {
      /** Format: int64 */
      amount?: number;
      per?: string;
    };
    Seller: {
      avatarUrl?: string;
      /** Format: uuid */
      id?: string;
      name?: string;
      role?: string;
    };
    SellerProfileV2: {
      /** Format: int64 */
      activeListingCount?: number;
      avatarUrl?: string;
      /** Format: uuid */
      id?: string;
      identity?: components['schemas']['TrustCheck'];
      /** Format: date-time */
      memberSince?: string;
      name?: string;
      /** Format: int64 */
      ownershipVerifiedListingCount?: number;
      responseStats?: components['schemas']['ResponseStats'];
      role?: string;
    };
    SessionsRevoked: {
      /** Format: int32 */
      revokedSessions?: number;
    };
    SessionView: {
      /** Format: date-time */
      createdAt?: string;
      current?: boolean;
      device?: string;
      /** Format: date-time */
      expiresAt?: string;
      /** Format: uuid */
      id?: string;
      /** Format: date-time */
      idleExpiresAt?: string;
      ipHint?: string;
      /** Format: date-time */
      lastSeenAt?: string;
      mfaVerified?: boolean;
    };
    ShareLink: {
      path?: string;
      role?: string;
      token?: string;
    };
    ShareRequest: {
      role?: string;
    };
    ShareState: {
      role?: string;
      shared?: boolean;
    };
    ShortlistDetail: {
      /** Format: uuid */
      id?: string;
      /** Format: int32 */
      itemCount?: number;
      /** Format: int32 */
      itemLimit?: number;
      items?: components['schemas']['EngagementItem'][];
      members?: components['schemas']['Member'][];
      muted?: boolean;
      name?: string;
      role?: string;
      share?: components['schemas']['ShareState'];
      /** Format: int64 */
      version?: number;
    };
    SignDepositRequest: {
      otpCode: string;
    };
    SignedUrls: {
      /** Format: date-time */
      expiresAt?: string;
      urls?: {
        [key: string]: string;
      };
    };
    SignRequest: {
      urls?: string[];
    };
    SiteInfoResponse: {
      operator?: components['schemas']['Info'];
      policies?: components['schemas']['PolicyLink'][];
    };
    Sla: {
      dailyDigestEnabled?: boolean;
      /** Format: int32 */
      firstResponseMinutes?: number;
      reminderEnabled?: boolean;
    };
    Source: {
      url?: string;
      /** Format: int32 */
      width?: number;
    };
    SseEmitter: {
      /** Format: int64 */
      timeout?: number;
    };
    StandardReasonResponse: {
      category?: string;
      code?: string;
      vietnameseLabel?: string;
    };
    Statistic: {
      /** Format: int64 */
      count?: number;
      /** Format: double */
      median?: number;
      method?: string;
      /** Format: int32 */
      minSamples?: number;
      purpose?: string;
      unit?: string;
    };
    StatusChange: {
      action?: string;
      fromStatus?: string;
      /** Format: uuid */
      listingId?: string;
      toStatus?: string;
    };
    StatusHistoryRow: {
      action?: string;
      /** Format: uuid */
      actorId?: string;
      actorName?: string;
      /** Format: date-time */
      createdAt?: string;
      fromStatus?: string;
      /** Format: uuid */
      id?: string;
      reason?: string;
      toStatus?: string;
    };
    StatusRequest: {
      action: string;
      reason: string;
    };
    StepMetric: {
      /** Format: int64 */
      count?: number;
      /** Format: double */
      percentage?: number;
      /** Format: int32 */
      stepIndex?: number;
      stepName?: string;
    };
    SubmitKycRequest: {
      address?: string;
      dob?: string;
      fullName: string;
      idCardBackUrl: string;
      idCardFrontUrl: string;
      idNumber: string;
      selfieUrl: string;
    };
    SubmitReportRequest: {
      /** @enum {string} */
      category: 'SCAM_DEPOSIT' | 'FAKE_SOLD' | 'INCORRECT_PRICE' | 'OTHER';
      description: string;
      evidenceUrls?: string;
      /** Format: uuid */
      listingId: string;
      reporterPhone?: string;
    };
    SubmitVerificationRequest: {
      certificateNumber?: string;
      documentUrls?: string;
      ownerNameOnDoc: string;
      /** @enum {string} */
      verificationType: 'CERTIFICATE_OF_OWNERSHIP' | 'POWER_OF_ATTORNEY' | 'PROJECT_PURCHASE_CONTRACT';
    };
    SuggestionDto: {
      drop?: string[];
      total?: components['schemas']['TotalDto'];
      type?: string;
    };
    Summary: {
      /** Format: uuid */
      id?: string;
      /** Format: int32 */
      itemCount?: number;
      /** Format: int32 */
      memberCount?: number;
      muted?: boolean;
      name?: string;
      role?: string;
      shared?: boolean;
      /** Format: date-time */
      updatedAt?: string;
      /** Format: int64 */
      version?: number;
    };
    TeamMember: {
      email?: string;
    };
    TokenStatusResponse: {
      status?: string;
    };
    TotalDto: {
      relation?: string;
      /** Format: int64 */
      value?: number;
    };
    TrendDay: {
      /** Format: date */
      day?: string;
      /** Format: int64 */
      detailViews?: number;
      /** Format: int64 */
      leadForms?: number;
      /** Format: int64 */
      leads?: number;
      /** Format: int64 */
      searches?: number;
    };
    Trust: {
      identity?: components['schemas']['TrustCheck'];
      listing?: components['schemas']['ListingCheck'];
      ownership?: components['schemas']['OwnershipCheck'];
    };
    TrustCheck: {
      /** Format: date-time */
      checkedAt?: string;
      /** Format: date-time */
      expiresAt?: string;
      status?: string;
    };
    TypeCount: {
      /** Format: int64 */
      count?: number;
      propertyType?: string;
      purpose?: string;
    };
    Unavailable: {
      slug?: string;
      title?: string;
    };
    UnreadCount: {
      /** Format: int64 */
      count?: number;
      /** Format: int64 */
      latestSeq?: number;
    };
    UnsubscribeDto: {
      applied?: boolean;
      category?: string;
      savedSearchName?: string;
      scope?: string;
    };
    UpdateAvatarRequest: {
      avatarMediaUrl?: string;
    };
    UpdateLeadStatusRequest: {
      /** Format: int64 */
      expectedVersion?: number;
      note?: string;
      /** @enum {string} */
      status: 'NEW' | 'CONTACTED' | 'APPOINTED' | 'CLOSED' | 'SPAM' | 'WITHDRAWN';
    };
    UpdateListingDraftRequest: {
      addressSummary?: string;
      areaM2?: number;
      /** Format: int32 */
      bathrooms?: number;
      /** Format: int32 */
      bedrooms?: number;
      /** Format: int64 */
      depositVnd?: number;
      description?: string;
      direction?: string;
      districtCode?: string;
      /** Format: int64 */
      expectedVersion?: number;
      /** Format: int32 */
      floors?: number;
      frontageM?: number;
      /** @enum {string} */
      furnishing?: 'NONE' | 'BASIC' | 'FULL';
      imageUrls?: string[];
      legalStatus?: string;
      /** @enum {string} */
      legalStatusCode?: 'RED_BOOK' | 'PINK_BOOK' | 'SALE_CONTRACT' | 'PENDING_CERTIFICATE' | 'OTHER';
      /** Format: int64 */
      monthlyServiceFeeVnd?: number;
      /** Format: int64 */
      priceVnd?: number;
      /** Format: uuid */
      projectId?: string;
      /** @enum {string} */
      propertyType?: 'APARTMENT' | 'HOUSE' | 'VILLA' | 'TOWNHOUSE' | 'LAND';
      provinceCode?: string;
      /** Format: double */
      publicLatitude?: number;
      /** Format: double */
      publicLongitude?: number;
      /** @enum {string} */
      purpose?: 'SALE' | 'RENT';
      roadWidthM?: number;
      title?: string;
      wardCode?: string;
    };
    UpdateProfileRequest: {
      avatarMediaUrl?: string;
      name: string;
      phone: string;
    };
    UpdateRequest: {
      alertBackOnMarket?: boolean;
      alertNew?: boolean;
      alertPriceDrop?: boolean;
      /** Format: int64 */
      expectedVersion?: number;
      frequency?: string;
      name?: string;
      paused?: boolean;
    };
    UpdateRoleRequest: {
      reason?: string;
      role?: string;
    };
    UpdateStatusRequest: {
      reason?: string;
      status?: string;
    };
    UploadedImage: {
      contentType?: string;
      objectKey?: string;
      /** Format: date-time */
      previewExpiresAt?: string;
      previewUrl?: string;
      /** Format: int64 */
      sizeBytes?: number;
      url?: string;
    };
    UserKycResponse: {
      address?: string;
      /** Format: date-time */
      createdAt?: string;
      dob?: string;
      /** Format: double */
      faceMatchScore?: number;
      fullName?: string;
      /** Format: uuid */
      id?: string;
      idCardBackUrl?: string;
      idCardFrontUrl?: string;
      maskedIdNumber?: string;
      rejectionReason?: string;
      selfieUrl?: string;
      /** @enum {string} */
      status?: 'PENDING' | 'VERIFIED' | 'REJECTED';
      /** Format: uuid */
      userId?: string;
      /** Format: date-time */
      verifiedAt?: string;
    };
    UserPage: {
      items?: components['schemas']['UserSummary'][];
      /** Format: int32 */
      page?: number;
      /** Format: int32 */
      size?: number;
      /** Format: int64 */
      total?: number;
    };
    UserSummary: {
      /** Format: date-time */
      createdAt?: string;
      email?: string;
      /** Format: date-time */
      emailVerifiedAt?: string;
      fullName?: string;
      /** Format: uuid */
      id?: string;
      kycStatus?: string;
      /** Format: date-time */
      lastLoginAt?: string;
      /** Format: int64 */
      listingCount?: number;
      /** Format: int32 */
      listingQuotaRemaining?: number;
      mfaEnrolled?: boolean;
      planCode?: string;
      /** Format: date-time */
      planExpiresAt?: string;
      role?: string;
      status?: string;
    };
    UserView: {
      avatarMediaUrl?: string;
      email?: string;
      /** Format: uuid */
      id?: string;
      /** Format: int32 */
      listingQuotaRemaining?: number;
      name?: string;
      phone?: string;
      planCode?: string;
      /** Format: date-time */
      planExpiresAt?: string;
      role?: string;
      roleLabel?: string;
    };
    ValidationErrorItem: {
      code?: string;
      field?: string;
      message?: string;
    };
    VerificationDecision: {
      /** Format: uuid */
      actorId?: string;
      actorName?: string;
      /** Format: date-time */
      createdAt?: string;
      decision?: string;
      /** Format: date-time */
      expiresAt?: string;
      /** Format: uuid */
      id?: string;
      note?: string;
      reasonCode?: string;
      reasonLabel?: string;
    };
    VerificationResult: {
      returnTo?: string;
      status?: string;
    };
    VerificationView: {
      /** Format: date-time */
      checkedAt?: string;
      complete?: boolean;
      /** Format: int64 */
      firstBrokenSeq?: number;
      /** Format: int64 */
      fromSeq?: number;
      /** Format: int64 */
      headSeq?: number;
      intact?: boolean;
      /** Format: int64 */
      staleUnchainedEvents?: number;
      /** Format: int64 */
      unchainedEvents?: number;
      /** Format: int64 */
      verifiedThrough?: number;
    };
    VersionSummary: {
      areaM2?: number;
      /** Format: date-time */
      moderatedAt?: string;
      price?: components['schemas']['ListingMoney'];
      propertyType?: string;
      purpose?: string;
      rejectionReason?: string;
      /** Format: uuid */
      revisionId?: string;
      /** Format: int32 */
      revisionNumber?: number;
      status?: string;
      /** Format: date-time */
      submittedAt?: string;
      title?: string;
    };
    VisibilityRequest: {
      hidden?: boolean;
    };
    VitalRow: {
      device?: string;
      /** Format: double */
      goodAtMost?: number;
      metric?: string;
      p75?: components['schemas']['Metric'];
      /** Format: double */
      poorAbove?: number;
      rating?: string;
      /** Format: int64 */
      samples?: number;
    };
    WindowView: {
      /** Format: int32 */
      days?: number;
      /** Format: date */
      from?: string;
      timezone?: string;
      /** Format: date */
      to?: string;
    };
    Withdraw: {
      /** Format: int64 */
      expectedVersion?: number;
      reason?: string;
    };
  };
  responses: never;
  parameters: never;
  requestBodies: never;
  headers: never;
  pathItems: never;
};
export type $defs = Record<string, never>;
export interface operations {
  run: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['VerificationView'];
        };
      };
    };
  };
  restart: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description Verification checkpoint reset */
      204: {
        headers: {
          [name: string]: unknown;
        };
        content?: never;
      };
    };
  };
  search_3: {
    parameters: {
      query?: {
        district?: string;
        ownerId?: string;
        page?: number;
        pendingEdit?: boolean;
        q?: string;
        size?: number;
        source?: string;
        status?: string;
      };
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ListingPage'];
        };
      };
    };
  };
  history_3: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['StatusHistoryRow'][];
        };
      };
    };
  };
  preview_2: {
    parameters: {
      query?: {
        revisionId?: string;
      };
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['Preview'];
        };
      };
    };
  };
  revisions: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['RevisionRow'][];
        };
      };
    };
  };
  changeStatus: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['StatusRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['StatusChange'];
        };
      };
    };
  };
  list_5: {
    parameters: {
      query?: {
        page?: number;
        query?: string;
        role?: string;
        size?: number;
        status?: string;
      };
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['UserPage'];
        };
      };
    };
  };
  history_2: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['AdminAction'][];
        };
      };
    };
  };
  kycAccessLog: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['AccessLogEntry'][];
        };
      };
    };
  };
  openKycDocuments: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['KycAccessRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['DocumentAccess'];
        };
      };
    };
  };
  resetMfa: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['ReasonRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content?: never;
      };
    };
  };
  updateRole: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['UpdateRoleRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['RoleChange'];
        };
      };
    };
  };
  revokeSessions: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['ReasonRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['SessionsRevoked'];
        };
      };
    };
  };
  updateStatus: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['UpdateStatusRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content?: never;
      };
    };
  };
  dashboard: {
    parameters: {
      query?: {
        area?: string;
        device?: string;
        from?: string;
        source?: string;
        to?: string;
      };
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['Dashboard'];
        };
      };
    };
  };
  getFunnelAnalytics: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['FunnelAnalyticsResponse'];
        };
      };
    };
  };
  leadFunnel: {
    parameters: {
      query?: {
        days?: number;
      };
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': {
            [key: string]: unknown;
          };
        };
      };
    };
  };
  getOverviewAnalytics: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ProductAnalyticsOverviewResponse'];
        };
      };
    };
  };
  cancel_1: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['Cancel'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['AppointmentView'];
        };
      };
    };
  };
  confirm: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['Confirm'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['AppointmentView'];
        };
      };
    };
  };
  outcome: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['Outcome'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['AppointmentView'];
        };
      };
    };
  };
  adminLogin: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['AdminLoginRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['AdminLoginResult'];
        };
      };
    };
  };
  beginEnrollment: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['ChallengeRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['Enrollment'];
        };
      };
    };
  };
  confirmEnrollment: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['MfaConfirmRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['EnrollmentCompleted'];
        };
      };
    };
  };
  verifyMfa: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['MfaVerifyRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['AuthResult'];
        };
      };
    };
  };
  forgotPassword: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['ForgotPasswordRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content?: never;
      };
    };
  };
  login: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['LoginRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['AuthResult'];
        };
      };
    };
  };
  logout: {
    parameters: {
      query?: never;
      header?: {
        Authorization?: string;
      };
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content?: never;
      };
    };
  };
  me: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['UserView'];
        };
      };
    };
  };
  updateMe: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['UpdateProfileRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['UserView'];
        };
      };
    };
  };
  updateAvatar: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['UpdateAvatarRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['UserView'];
        };
      };
    };
  };
  passwordResetStatus: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['IamTokenRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['TokenStatusResponse'];
        };
      };
    };
  };
  register: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['RegisterRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['RegistrationResult'];
        };
      };
    };
  };
  resendVerification: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['ResendVerificationRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content?: never;
      };
    };
  };
  resetPassword: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['ResetPasswordRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content?: never;
      };
    };
  };
  verifyEmail: {
    parameters: {
      query: {
        token: string;
      };
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content?: never;
      };
    };
  };
  verifyEmailPost: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['IamTokenRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['VerificationResult'];
        };
      };
    };
  };
  bank: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['BankSettings'];
        };
      };
    };
  };
  bank_1: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['BankRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['BankSettings'];
        };
      };
    };
  };
  orders: {
    parameters: {
      query?: {
        page?: number;
        q?: string;
        size?: number;
        status?: string;
      };
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['AdminOrderPage'];
        };
      };
    };
  };
  orderDetail: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['OrderDetail'];
        };
      };
    };
  };
  queue_1: {
    parameters: {
      query?: {
        page?: number;
        size?: number;
        status?: string;
      };
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['AdminOrderPage'];
        };
      };
    };
  };
  approve_2: {
    parameters: {
      query?: never;
      header?: {
        'Idempotency-Key'?: string;
      };
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody?: {
      content: {
        'application/json': components['schemas']['ReviewRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['Order'];
        };
      };
    };
  };
  receipt: {
    parameters: {
      query?: never;
      header?: {
        'Idempotency-Key'?: string;
      };
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['ReceiptRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['Order'];
        };
      };
    };
  };
  reject_2: {
    parameters: {
      query?: never;
      header?: {
        'Idempotency-Key'?: string;
      };
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['RejectRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['Order'];
        };
      };
    };
  };
  resolve: {
    parameters: {
      query?: never;
      header?: {
        'Idempotency-Key'?: string;
      };
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['ResolveRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['Order'];
        };
      };
    };
  };
  mine_1: {
    parameters: {
      query?: {
        page?: number;
        size?: number;
      };
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['OrderPage'];
        };
      };
    };
  };
  create_3: {
    parameters: {
      query?: never;
      header?: {
        'Idempotency-Key'?: string;
      };
      path?: never;
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['PlanRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['Order'];
        };
      };
    };
  };
  mineDetail: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['OrderDetail'];
        };
      };
    };
  };
  cancel: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['Order'];
        };
      };
    };
  };
  report: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['Order'];
        };
      };
    };
  };
  plans: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['Plan'][];
        };
      };
    };
  };
  leadReport: {
    parameters: {
      query?: {
        from?: string;
        to?: string;
      };
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': {
            [key: string]: unknown;
          };
        };
      };
    };
  };
  team: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': {
            [key: string]: unknown;
          }[];
        };
      };
    };
  };
  addMember: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['TeamMember'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': {
            [key: string]: unknown;
          }[];
        };
      };
    };
  };
  removeMember_1: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        memberId: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': {
            [key: string]: unknown;
          }[];
        };
      };
    };
  };
  get_1: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': {
            [key: string]: unknown;
          };
        };
      };
    };
  };
  sla: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['Sla'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': {
            [key: string]: unknown;
          };
        };
      };
    };
  };
  getProjects: {
    parameters: {
      query?: {
        keyword?: string;
        page?: number;
        size?: number;
      };
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ProjectResponse'][];
        };
      };
    };
  };
  createProject: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['CreateProjectRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ProjectResponse'];
        };
      };
    };
  };
  getProjectById: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ProjectResponse'];
        };
      };
    };
  };
  profile: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['CatalogPublicProfileResponse'];
        };
      };
    };
  };
  updateProfile: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['ProfileInput'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['CatalogPublicProfileResponse'];
        };
      };
    };
  };
  getProjectBySlug: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        slug: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ProjectResponse'];
        };
      };
    };
  };
  list_2: {
    parameters: {
      query?: {
        category?: 'LEGAL_POLICY' | 'KNOWLEDGE' | 'MARKET_INSIGHTS';
        page?: number;
        size?: number;
        status?: 'DRAFT' | 'SUBMITTED' | 'SCHEDULED' | 'PUBLISHED' | 'SUPERSEDED' | 'ARCHIVED' | 'REJECTED';
      };
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ArticleResponse'][];
        };
      };
    };
  };
  create_2: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['CreateArticleRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ArticleResponse'];
        };
      };
    };
  };
  detail_1: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ArticleResponse'];
        };
      };
    };
  };
  newRevision: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['RevisionRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ArticleResponse'];
        };
      };
    };
  };
  updateDraft_1: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
        revisionId: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['RevisionRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ArticleResponse'];
        };
      };
    };
  };
  approve_1: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
        revisionId: string;
      };
      cookie?: never;
    };
    requestBody?: {
      content: {
        'application/json': components['schemas']['ApproveRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['RevisionResponse'];
        };
      };
    };
  };
  previewLink: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
        revisionId: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['PreviewLinkResponse'];
        };
      };
    };
  };
  reject_1: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
        revisionId: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['RejectRevisionRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['RevisionResponse'];
        };
      };
    };
  };
  submit: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
        revisionId: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['RevisionResponse'];
        };
      };
    };
  };
  cancelSchedule: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ArticleResponse'];
        };
      };
    };
  };
  unpublish: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ArticleResponse'];
        };
      };
    };
  };
  ingest: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['IngestionResult'];
        };
      };
    };
  };
  consent: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ConsentReceipt'];
        };
      };
    };
  };
  approveKyc: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody?: {
      content: {
        'application/json': components['schemas']['RejectKycRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['UserKycResponse'];
        };
      };
    };
  };
  rejectKyc: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['RejectKycRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['UserKycResponse'];
        };
      };
    };
  };
  revokeKyc: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['RejectKycRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['UserKycResponse'];
        };
      };
    };
  };
  grantDocumentAccess: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['DocumentAccessRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['KycDocumentAccess'];
        };
      };
    };
  };
  myStatus: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['MyKycStatus'];
        };
      };
    };
  };
  getQueue_2: {
    parameters: {
      query?: {
        page?: number;
        size?: number;
        status?: 'PENDING' | 'VERIFIED' | 'REJECTED';
      };
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['UserKycResponse'][];
        };
      };
    };
  };
  submitKyc: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['SubmitKycRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['UserKycResponse'];
        };
      };
    };
  };
  getKycByUserId: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        userId: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['UserKycResponse'];
        };
      };
    };
  };
  getOwnDocuments: {
    parameters: {
      query?: never;
      header: {
        'X-Kyc-Document-Access': string;
      };
      path: {
        userId: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['KycDocumentsResponse'];
        };
      };
    };
  };
  getLeads: {
    parameters: {
      query?: {
        brokerId?: string;
        listingId?: string;
        page?: number;
        size?: number;
      };
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['LeadLeadItem'][];
        };
      };
    };
  };
  getLead: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['LeadLeadItem'];
        };
      };
    };
  };
  leadAppointments: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['AppointmentView'][];
        };
      };
    };
  };
  propose_1: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['Propose'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['AppointmentView'];
        };
      };
    };
  };
  assign: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['Assign'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['LeadLeadItem'];
        };
      };
    };
  };
  revealLeadContact: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': {
            [key: string]: string;
          };
        };
      };
    };
  };
  history_1: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': {
            [key: string]: unknown;
          }[];
        };
      };
    };
  };
  qualify: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['Qualify'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['LeadLeadItem'];
        };
      };
    };
  };
  updateLeadStatus: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['UpdateLeadStatusRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['LeadLeadItem'];
        };
      };
    };
  };
  searchLeads_1: {
    parameters: {
      query?: {
        assigneeId?: string;
        listingId?: string;
        overdue?: boolean;
        page?: number;
        q?: string;
        qualification?: string;
        requestType?: 'VIEWING' | 'CONSULTATION';
        size?: number;
        status?: 'NEW' | 'CONTACTED' | 'APPOINTED' | 'CLOSED' | 'SPAM' | 'WITHDRAWN';
      };
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['LeadLeadItem2'];
        };
      };
    };
  };
  getLeadListings: {
    parameters: {
      query?: {
        page?: number;
        q?: string;
        size?: number;
      };
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['LeadListingPageResponse'];
        };
      };
    };
  };
  report_1: {
    parameters: {
      query?: {
        from?: string;
        to?: string;
      };
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': {
            [key: string]: unknown;
          };
        };
      };
    };
  };
  searchLeads: {
    parameters: {
      query?: {
        assigneeId?: string;
        listingId?: string;
        overdue?: boolean;
        page?: number;
        q?: string;
        qualification?: string;
        requestType?: 'VIEWING' | 'CONSULTATION';
        size?: number;
        status?: 'NEW' | 'CONTACTED' | 'APPOINTED' | 'CLOSED' | 'SPAM' | 'WITHDRAWN';
      };
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['LeadLeadItem2'];
        };
      };
    };
  };
  getSentLeads: {
    parameters: {
      query?: {
        page?: number;
        size?: number;
        status?: 'NEW' | 'CONTACTED' | 'APPOINTED' | 'CLOSED' | 'SPAM' | 'WITHDRAWN';
      };
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['LeadInquiryItem2'];
        };
      };
    };
  };
  createDraft: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['CreateListingDraftRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': {
            [key: string]: unknown;
          };
        };
      };
    };
  };
  getListingById: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ListingDetailResponse'];
        };
      };
    };
  };
  updateDraft: {
    parameters: {
      query?: never;
      header?: {
        'If-Match'?: string;
      };
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['UpdateListingDraftRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': {
            [key: string]: unknown;
          };
        };
      };
    };
  };
  submitRevision: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': {
            [key: string]: unknown;
          };
        };
      };
    };
  };
  changeVisibility: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['VisibilityRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': {
            [key: string]: unknown;
          };
        };
      };
    };
  };
  submitVerification: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        listingId: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['SubmitVerificationRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ListingVerificationResponse'];
        };
      };
    };
  };
  getAllForAdmin: {
    parameters: {
      query?: {
        page?: number;
        size?: number;
      };
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ListingDetailResponse'][];
        };
      };
    };
  };
  getListingBySlug: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        slug: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ListingDetailResponse'];
        };
      };
    };
  };
  estimatePrice: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['EstimatePriceRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['EstimatePriceResponse'];
        };
      };
    };
  };
  getMyListings: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ListingDetailResponse'][];
        };
      };
    };
  };
  calculateQualityScore: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['QualityScoreRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['QualityScoreResponse'];
        };
      };
    };
  };
  search_2: {
    parameters: {
      query?: {
        keyword?: string;
        maxArea?: string;
        maxLat?: string;
        maxLng?: string;
        maxPrice?: string;
        minArea?: string;
        minLat?: string;
        minLng?: string;
        minPrice?: string;
        page?: number;
        propertyType?: string;
        purpose?: string;
        size?: number;
        sortBy?: string;
      };
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ListingSummaryResponse'][];
        };
      };
    };
  };
  becomeOwner: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: {
      content: {
        'application/json': components['schemas']['BecomeOwnerRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['BecomeOwnerResult'];
        };
      };
    };
  };
  list_4: {
    parameters: {
      query?: {
        page?: number;
        size?: number;
        status?: 'NEW' | 'CONTACTED' | 'APPOINTED' | 'CLOSED' | 'SPAM' | 'WITHDRAWN';
      };
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['LeadInquiryItem2'];
        };
      };
    };
  };
  appointments: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['AppointmentView'][];
        };
      };
    };
  };
  propose: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['Propose'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['AppointmentView'];
        };
      };
    };
  };
  history: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': {
            [key: string]: unknown;
          }[];
        };
      };
    };
  };
  withdraw: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['Withdraw'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['LeadInquiryItem'];
        };
      };
    };
  };
  eligibility: {
    parameters: {
      query: {
        listingId: string;
      };
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': {
            [key: string]: unknown;
          };
        };
      };
    };
  };
  mfaStatus: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['IamStatus'];
        };
      };
    };
  };
  regenerateRecoveryCodes: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['CodeRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['RecoveryCodes'];
        };
      };
    };
  };
  list: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['PreferenceDto'][];
        };
      };
    };
  };
  update: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['PreferenceDto'][];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['PreferenceDto'][];
        };
      };
    };
  };
  changePassword: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['ChangePasswordRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['RevokedCount'];
        };
      };
    };
  };
  page_1: {
    parameters: {
      query?: {
        cursor?: string;
        size?: number;
      };
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['SavedListingPage'];
        };
      };
    };
  };
  save: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        listingId: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['SavedState'];
        };
      };
    };
  };
  unsave: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        listingId: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content?: never;
      };
    };
  };
  ids: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['SavedIds'];
        };
      };
    };
  };
  list_1: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['SavedSearchList'];
        };
      };
    };
  };
  create_1: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['CreateRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['SavedSearchDto'];
        };
      };
    };
  };
  delete_1: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content?: never;
      };
    };
  };
  update_1: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['UpdateRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['SavedSearchDto'];
        };
      };
    };
  };
  securityEvents: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['IamEvent'][];
        };
      };
    };
  };
  sessions: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['SessionView'][];
        };
      };
    };
  };
  revoke_1: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        sessionId: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content?: never;
      };
    };
  };
  revokeOthers: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['RevokedCount'];
        };
      };
    };
  };
  mine: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['Summary'][];
        };
      };
    };
  };
  create: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['NameRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ShortlistDetail'];
        };
      };
    };
  };
  get: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ShortlistDetail'];
        };
      };
    };
  };
  delete: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content?: never;
      };
    };
  };
  rename: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['RenameRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ShortlistDetail'];
        };
      };
    };
  };
  addItem: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
        listingId: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ShortlistDetail'];
        };
      };
    };
  };
  removeItem: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
        listingId: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ShortlistDetail'];
        };
      };
    };
  };
  removeMember: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
        memberId: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ShortlistDetail'];
        };
      };
    };
  };
  setRole: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
        memberId: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['ShareRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ShortlistDetail'];
        };
      };
    };
  };
  leave: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content?: never;
      };
    };
  };
  mute: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['MuteRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ShortlistDetail'];
        };
      };
    };
  };
  share: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['ShareRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ShareLink'];
        };
      };
    };
  };
  revoke: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content?: never;
      };
    };
  };
  join: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['EngagementTokenRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ShortlistDetail'];
        };
      };
    };
  };
  upload: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: {
      content: {
        'multipart/form-data': {
          /** Format: binary */
          file: string;
        };
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['UploadedImage'];
        };
      };
    };
  };
  delete_3: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        objectKey: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': {
            [key: string]: boolean;
          };
        };
      };
    };
  };
  uploadKyc: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: {
      content: {
        'multipart/form-data': {
          /** Format: binary */
          file: string;
        };
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['UploadedImage'];
        };
      };
    };
  };
  readKyc: {
    parameters: {
      query?: never;
      header?: {
        'X-Kyc-Document-Access'?: string;
      };
      path: {
        objectKey: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': string;
        };
      };
    };
  };
  sign: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['SignRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['SignedUrls'];
        };
      };
    };
  };
  readSigned: {
    parameters: {
      query?: {
        exp?: number;
        sig?: string;
      };
      header?: never;
      path: {
        objectKey: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': string;
        };
      };
    };
  };
  auditSamples: {
    parameters: {
      query?: {
        page?: number;
        size?: number;
        status?: string;
      };
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['SamplePage'];
        };
      };
    };
  };
  reviewSample: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['AuditReviewRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content?: never;
      };
    };
  };
  drawSamples: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': {
            [key: string]: number;
          };
        };
      };
    };
  };
  bulk: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['BulkRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['BulkResult'];
        };
      };
    };
  };
  decideDuplicate: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        candidateId: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['DuplicateDecisionRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content?: never;
      };
    };
  };
  approve: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['ApproveListingRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ModerationDecision'];
        };
      };
    };
  };
  claim_1: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ClaimView'];
        };
      };
    };
  };
  release_1: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content?: never;
      };
    };
  };
  decisions: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['DecisionView'][];
        };
      };
    };
  };
  getDiff: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ListingDiffResponse'];
        };
      };
    };
  };
  duplicates: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['Candidate'][];
        };
      };
    };
  };
  reject: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['RejectListingRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ModerationDecision'];
        };
      };
    };
  };
  getQueue_1: {
    parameters: {
      query?: {
        filter?: string;
        page?: number;
        size?: number;
      };
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ModerationQueuePage'];
        };
      };
    };
  };
  reasons_1: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': {
            [key: string]: components['schemas']['StandardReasonResponse'][];
          };
        };
      };
    };
  };
  getRejectionReasons: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['StandardReasonResponse'][];
        };
      };
    };
  };
  recent: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['NotificationView'][];
        };
      };
    };
  };
  delete_2: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content?: never;
      };
    };
  };
  read: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content?: never;
      };
    };
  };
  feed: {
    parameters: {
      query?: {
        before?: string;
        size?: string;
        unread?: string;
      };
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['Feed'];
        };
      };
    };
  };
  readAll: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['ReadAllRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['UnreadCount'];
        };
      };
    };
  };
  stream: {
    parameters: {
      query?: {
        lastEventId?: string;
      };
      header?: {
        'Last-Event-ID'?: string;
      };
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          'text/event-stream': components['schemas']['SseEmitter'];
        };
      };
    };
  };
  unreadCount: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['UnreadCount'];
        };
      };
    };
  };
  list_3: {
    parameters: {
      query?: {
        category?: 'LEGAL_POLICY' | 'KNOWLEDGE' | 'MARKET_INSIGHTS';
        page?: number;
        size?: number;
      };
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['PublicArticleResponse'][];
        };
      };
    };
  };
  bySlug_1: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        slug: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['PublicArticleResponse'];
        };
      };
    };
  };
  preview_1: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        token: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['PublicArticleResponse'];
        };
      };
    };
  };
  search_1: {
    parameters: {
      query: {
        q: string;
      };
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': string;
        };
      };
    };
  };
  submitLead: {
    parameters: {
      query?: never;
      header?: {
        'Idempotency-Key'?: string;
      };
      path?: never;
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['CreateLeadRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': {
            [key: string]: unknown;
          };
        };
      };
    };
  };
  read_1: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        objectKey: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': string;
        };
      };
    };
  };
  getProfile: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        ownerId: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ListingPublicProfileResponse'];
        };
      };
    };
  };
  listings_1: {
    parameters: {
      query?: {
        page?: number;
        size?: number;
      };
      header?: never;
      path: {
        ownerId: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['PublicListingCard'][];
        };
      };
    };
  };
  submitReport: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['SubmitReportRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ListingReportResponse'];
        };
      };
    };
  };
  robots: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': string;
        };
      };
    };
  };
  index: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': string;
        };
      };
    };
  };
  part: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        name: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': string;
        };
      };
    };
  };
  publicView: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        token: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['PublicShortlist'];
        };
      };
    };
  };
  siteInfo: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['SiteInfoResponse'];
        };
      };
    };
  };
  describe: {
    parameters: {
      query: {
        token: string;
      };
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['UnsubscribeDto'];
        };
      };
    };
  };
  apply: {
    parameters: {
      query: {
        token: string;
      };
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['UnsubscribeDto'];
        };
      };
    };
  };
  getReports: {
    parameters: {
      query?: {
        page?: number;
        severity?: 'P0_EMERGENCY' | 'HIGH' | 'MEDIUM' | 'LOW';
        size?: number;
        status?: 'PENDING' | 'WAITING_REPLY' | 'RESOLVED' | 'DISMISSED' | 'APPEALED';
      };
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ListingReportResponse'][];
        };
      };
    };
  };
  getReportById: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ListingReportResponse'];
        };
      };
    };
  };
  appealReport: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['AppealReportRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ListingReportResponse'];
        };
      };
    };
  };
  claim: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['Claim'];
        };
      };
    };
  };
  release: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content?: never;
      };
    };
  };
  dismissReport: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['DismissReportRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ListingReportResponse'];
        };
      };
    };
  };
  emergencyHideListing: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody?: {
      content: {
        'application/json': {
          [key: string]: string;
        };
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ListingReportResponse'];
        };
      };
    };
  };
  events: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['LeadEvent'][];
        };
      };
    };
  };
  addNote: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': {
          [key: string]: string;
        };
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content?: never;
      };
    };
  };
  resolveReport: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['ResolveReportRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ListingReportResponse'];
        };
      };
    };
  };
  escalate: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': {
          [key: string]: string;
        };
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content?: never;
      };
    };
  };
  queue: {
    parameters: {
      query?: {
        breached?: boolean;
        mine?: boolean;
        page?: number;
        severity?: string;
        size?: number;
        status?: string;
      };
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['LeadQueuePage'];
        };
      };
    };
  };
  createContract: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['CreateDepositRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['DepositContractResponse'];
        };
      };
    };
  };
  getContract: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['DepositContractResponse'];
        };
      };
    };
  };
  refundEscrow: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody?: {
      content: {
        'application/json': components['schemas']['DepositActionRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['DepositContractResponse'];
        };
      };
    };
  };
  releaseEscrow: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody?: {
      content: {
        'application/json': components['schemas']['DepositActionRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['DepositContractResponse'];
        };
      };
    };
  };
  signByBuyer: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['SignDepositRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['DepositContractResponse'];
        };
      };
    };
  };
  signBySeller: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['SignDepositRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['DepositContractResponse'];
        };
      };
    };
  };
  getContractsByListing: {
    parameters: {
      query?: {
        page?: number;
        size?: number;
      };
      header?: never;
      path: {
        listingId: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['DepositContractResponse'][];
        };
      };
    };
  };
  getQueue: {
    parameters: {
      query?: {
        page?: number;
        size?: number;
        status?: 'PENDING' | 'VERIFIED_OWNER' | 'REJECTED' | 'REVOKED';
      };
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ListingVerificationResponse'][];
        };
      };
    };
  };
  getVerificationDetail: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ListingVerificationResponse'];
        };
      };
    };
  };
  approveVerification: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody?: {
      content: {
        'application/json': components['schemas']['ApproveVerificationRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ListingVerificationResponse'];
        };
      };
    };
  };
  openDocuments: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': {
          [key: string]: string;
        };
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['DocumentAccess'];
        };
      };
    };
  };
  getEvidence: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['Evidence'];
        };
      };
    };
  };
  rejectVerification: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['RejectVerificationRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ListingVerificationResponse'];
        };
      };
    };
  };
  revokeVerification: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['RejectVerificationRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ListingVerificationResponse'];
        };
      };
    };
  };
  reasons: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': {
            [key: string]: {
              [key: string]: string;
            }[];
          };
        };
      };
    };
  };
  progress: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': {
            [key: string]: unknown;
          };
        };
      };
    };
  };
  enqueue: {
    parameters: {
      query?: {
        limit?: number;
      };
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': {
            [key: string]: unknown;
          };
        };
      };
    };
  };
  status: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['SearchStatus'];
        };
      };
    };
  };
  cleanup: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['SearchStatus'];
        };
      };
    };
  };
  rebuild: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['SearchStatus'];
        };
      };
    };
  };
  rollback: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['SearchStatus'];
        };
      };
    };
  };
  detail: {
    parameters: {
      query?: never;
      header?: {
        'If-None-Match'?: string;
      };
      path: {
        slugOrId: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ListingDetailV2'];
        };
      };
    };
  };
  priceHistory: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        slugOrId: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['PriceHistoryResponse'];
        };
      };
    };
  };
  similar: {
    parameters: {
      query?: {
        size?: number;
      };
      header?: never;
      path: {
        slugOrId: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ListingSummaryV2'][];
        };
      };
    };
  };
  map: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['MapResponseV2'];
        };
      };
    };
  };
  search: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['SearchResponseV2'];
        };
      };
    };
  };
  myListings: {
    parameters: {
      query?: {
        page?: number;
        size?: number;
        status?: string;
      };
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['MyListingsPage'];
        };
      };
    };
  };
  confirmAvailability: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': {
            [key: string]: unknown;
          };
        };
      };
    };
  };
  draft: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['DraftView'];
        };
      };
    };
  };
  preview: {
    parameters: {
      query?: {
        version?: string;
      };
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['PreviewView'];
        };
      };
    };
  };
  renew: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': {
            [key: string]: unknown;
          };
        };
      };
    };
  };
  importCsv: {
    parameters: {
      query?: {
        dryRun?: boolean;
      };
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: {
      content: {
        'multipart/form-data': {
          /** Format: binary */
          file: string;
        };
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ImportReport'];
        };
      };
    };
  };
  importTemplate: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          'text/csv': string;
        };
      };
    };
  };
  areas: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['AreaList'];
        };
      };
    };
  };
  area: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        slug: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['AreaPage'];
        };
      };
    };
  };
  page: {
    parameters: {
      query?: {
        category?: 'LEGAL_POLICY' | 'KNOWLEDGE' | 'MARKET_INSIGHTS';
        page?: number;
        size?: number;
      };
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['PublicPageResponse'];
        };
      };
    };
  };
  bySlug: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        slug: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['PublicArticleResponse'];
        };
      };
    };
  };
  home: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['Home'];
        };
      };
    };
  };
  projects: {
    parameters: {
      query?: {
        district?: string;
        page?: number;
        size?: number;
      };
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ProjectList'];
        };
      };
    };
  };
  project: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        slug: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ProjectPage'];
        };
      };
    };
  };
  profile_1: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        sellerId: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['SellerProfileV2'];
        };
      };
    };
  };
  listings: {
    parameters: {
      query?: {
        cursor?: string;
        size?: number;
      };
      header?: never;
      path: {
        sellerId: string;
      };
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['SearchResponseV2'];
        };
      };
    };
  };
  error: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ProblemDetails'];
        };
      };
    };
  };
  error_2: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ProblemDetails'];
        };
      };
    };
  };
  error_1: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ProblemDetails'];
        };
      };
    };
  };
  error_3: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ProblemDetails'];
        };
      };
    };
  };
  error_6: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ProblemDetails'];
        };
      };
    };
  };
  error_5: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ProblemDetails'];
        };
      };
    };
  };
  error_4: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ProblemDetails'];
        };
      };
    };
  };
  render: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': string;
        };
      };
    };
  };
  render_1: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': string;
        };
      };
    };
  };
}
