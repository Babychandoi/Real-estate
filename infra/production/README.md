# Production readiness overlay

`compose.production-overlay.yaml` adds PgBouncer and automatic TLS ingress without changing the local stack. Set `PUBLIC_HOST` and a read-only `PGBOUNCER_USERLIST_FILE`, then combine it with `infra/compose.yaml`.

`k8s/production-platform.yaml` is intentionally a deployment template: replace placeholder host/storage, install the named operators, and connect the `ClusterSecretStore` to the organisation's Vault/cloud secret manager. PostgreSQL becomes a three-instance cluster through CloudNativePG. Redis HA (Sentinel/cluster), a distributed four-drive MinIO Tenant and CDN/DNS are provider/operator resources and must be selected for the target cluster; they cannot truthfully be marked deployed from a workstation.

Run `scripts/run-load-test.ps1 -Profile load -BaseUrl https://your-host` and then `-Profile soak`. Evidence is written under `.artifacts/k6`. Run `scripts/backup-restore-drill.ps1` against the local Compose project; the drill never restores over the live database.

## Local Kubernetes HA installation

The live development cluster uses context `kind-bds-production-local` and the source-controlled values under `infra/k8s/values`. It contains Redis replication with Sentinel quorum, a four-server MinIO Tenant, three-member Vault Raft, External Secrets Operator, cert-manager, ingress-nginx and two frontend replicas. The local HTTPS/DNS endpoint is `https://bds.127.0.0.1.nip.io:8443` (the certificate is signed by the cluster-local CA).

Vault bootstrap material is intentionally excluded from Git and protected with Windows DPAPI under `.artifacts/vault`. A limited, renewable Vault token is used by `vault-external-secret.yaml`; the root token is not placed in an application Secret. For a public deployment, replace DPAPI/manual unseal with a cloud KMS auto-unseal configuration.

The local wildcard DNS proves ingress and TLS routing, but it is not an authoritative production zone or CDN. Public Cloudflare/Akamai/Fastly activation still requires the owner domain, provider account and API token; do not place those credentials in this repository.

## Temporary Cloudflare Tunnel origin

`nhadatchuan.online` and `www.nhadatchuan.online` are currently published through the named Cloudflare Tunnel `nhadatchuan-online`. On this workstation the live configuration is stored outside the repository at `%USERPROFILE%\.cloudflared\config.yml`; the safe template is `infra/cloudflared/config.example.yml`.

The tunnel connector now runs as the `cloudflared` service in Docker Compose. Its local ingress routes the public hostnames to `http://frontend:3000`, which also proxies `/api` to the backend. Keep Docker Desktop and the Compose stack running. No router port-forwarding is needed. The Git-ignored runtime config and the credential mounted from `%USERPROFILE%\.cloudflared` never enter the image.

Useful checks from PowerShell:

```powershell
& 'C:\Program Files (x86)\cloudflared\cloudflared.exe' tunnel ingress validate
& 'C:\Program Files (x86)\cloudflared\cloudflared.exe' tunnel info nhadatchuan-online
Invoke-WebRequest https://nhadatchuan.online/healthz -UseBasicParsing
Invoke-WebRequest 'https://nhadatchuan.online/api/v1/listings/search?page=0&size=1' -UseBasicParsing
```

The old `BDS-Cloudflare-Tunnel` scheduled task is disabled to avoid duplicate connectors. Docker Desktop must be configured to start with Windows if unattended recovery after a reboot is required. Never commit the tunnel token, tunnel credential JSON or `%USERPROFILE%\.cloudflared\cert.pem`.
