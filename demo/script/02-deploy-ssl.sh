#!/usr/bin/env bash
# 02-deploy-ssl.sh — issue a Let's Encrypt cert and wire HTTPS into the site.
# Run as: sudo bash script/02-deploy-ssl.sh
#
# Requires: 01-deploy-nginx.sh already applied and reachable at port 80.

set -euo pipefail

if [[ $EUID -ne 0 ]]; then
  echo "must be run as root (use: sudo bash $0)" >&2
  exit 1
fi

SITE=jvolution.superb.today

if ! command -v certbot >/dev/null 2>&1; then
  echo "certbot not found — install with: sudo apt install certbot python3-certbot-nginx" >&2
  exit 1
fi

# --nginx plugin rewrites the conf to add the 443 server block, the redirect,
# and the certificate paths. Non-interactive: reuses the email/TOS state from
# previous certs (e.g. aze.superb.today).
certbot --nginx \
  --non-interactive \
  --agree-tos \
  --redirect \
  -d "${SITE}"

nginx -t
systemctl reload nginx

echo
echo "OK — https://${SITE}/"
