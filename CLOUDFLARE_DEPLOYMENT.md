# Cloudflare Deployment Checklist

AuditIQ remains hosted on Emergent. Cloudflare provides DNS, TLS, CDN, WAF, and DDoS protection in front of the permanent Emergent production hostname. Do not point Cloudflare at the temporary preview URL.

## Prerequisites

- Complete the production deployment in Emergent and copy its permanent hostname.
- Add the intended custom hostname in Emergent through **Link domain** / Entri before enabling the Cloudflare proxy.
- Preserve existing MX, TXT, SPF, DKIM, DMARC, and unrelated DNS records.

## Cloudflare DNS

Create the application hostname after Emergent accepts it:

```text
Type: CNAME
Name: app
Target: <permanent-emergent-production-hostname>
TTL: Auto
Proxy: DNS only during hostname verification, then Proxied
```

For an apex domain, use Cloudflare CNAME flattening and the exact target supplied by Emergent. Never invent an origin IP.

## SSL/TLS

- Set SSL/TLS encryption mode to **Full (strict)**.
- Enable **Always Use HTTPS** only after direct-origin HTTPS and the custom hostname certificate both work.
- Keep minimum TLS at 1.2 or higher.
- Never use Flexible mode as a workaround for certificate errors.

## Cache rules

Create a bypass rule before enabling the proxy:

```text
Hostname equals <custom-hostname>
AND URI path starts with /api/
Then Cache eligibility = Bypass cache
```

The application also sends `Cache-Control: no-store` for every `/api/` response. HTML revalidates on each visit; static JavaScript and images cache for one day and support gzip.

## Health checks

- Frontend: `GET /healthz` returns `200 ok`.
- Backend: `GET /api/health` returns `200 {"status":"ok"}`.
- Diagnostics: `GET /api/health/config` returns only boolean readiness signals and storage mode; it never returns secrets.

## Validation

```bash
curl -i https://<custom-hostname>/healthz
curl -i https://<custom-hostname>/api/health
curl -i https://<custom-hostname>/api/health/config
```

Confirm in browser developer tools that API calls remain relative `/api/...`, responses have no CORS errors, `/api` responses are not cached, and static assets can become Cloudflare cache hits.

## Rollback

If the custom hostname fails, switch the CNAME from **Proxied** to **DNS only** to isolate Cloudflare. If the origin hostname or certificate is wrong, restore the previous DNS record and correct the custom hostname in Emergent. Do not expose MongoDB or place credentials in Cloudflare DNS, frontend variables, or cache rules.