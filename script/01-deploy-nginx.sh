#!/usr/bin/env bash
# 01-deploy-nginx.sh — install/refresh the jvolution.superb.today nginx site.
# Run as: sudo bash script/01-deploy-nginx.sh
#
# Prereq: bash script/00-build.sh has produced ./public.
# Idempotent. SSL-aware: if /etc/letsencrypt/live/$SITE exists, writes the full
# HTTPS + 80→443 redirect conf so re-running after 02-deploy-ssl.sh does not
# wipe HTTPS. Otherwise writes a bare port-80 conf for the cert-issuance step.

set -euo pipefail

if [[ $EUID -ne 0 ]]; then
  echo "must be run as root (use: sudo bash $0)" >&2
  exit 1
fi

SITE=jvolution.superb.today
ROOT=/home/jose/dev/jvolution/public
AVAIL=/etc/nginx/sites-available/${SITE}.conf
ENABLED=/etc/nginx/sites-enabled/${SITE}.conf
LIVE_DIR=/etc/letsencrypt/live/${SITE}

if [[ ! -f "$ROOT/Sonar Tamagotchi.html" ]]; then
  echo "deploy root not built: $ROOT" >&2
  echo "run: bash script/00-build.sh" >&2
  exit 1
fi

# Body shared by HTTP and HTTPS variants — only the listen/cert lines differ.
# Note: a `types {}` block at server scope would *replace* the inherited
# /etc/nginx/mime.types and make every other extension fall back to
# default_type (octet-stream → browser download). Override .jsx in its own
# location so the global mime map keeps applying to html/css/etc.
read -r -d '' SITE_BODY <<EOF || true
    server_name ${SITE};

    root ${ROOT};
    index "Sonar Tamagotchi.html";

    charset utf-8;

    # Earlier deployments mis-served the index as application/octet-stream,
    # which Chrome caches as "this URL is a download". Force-revalidate so the
    # bad cached response is invalidated on next visit.
    add_header Cache-Control "no-cache, no-store, must-revalidate" always;
    add_header Pragma "no-cache" always;
    add_header Expires "0" always;
    add_header X-Content-Type-Options "nosniff" always;

    location ~ \.jsx\$ {
        types { }
        default_type text/babel;
    }

    location / {
        try_files \$uri \$uri/ =404;
    }
EOF

if [[ -d "$LIVE_DIR" ]]; then
  cat > "$AVAIL" <<EOF
server {
${SITE_BODY}

    listen 443 ssl;
    ssl_certificate ${LIVE_DIR}/fullchain.pem;
    ssl_certificate_key ${LIVE_DIR}/privkey.pem;
    include /etc/letsencrypt/options-ssl-nginx.conf;
    ssl_dhparam /etc/letsencrypt/ssl-dhparams.pem;
}

server {
    if (\$host = ${SITE}) {
        return 301 https://\$host\$request_uri;
    }

    listen 80;
    server_name ${SITE};
    return 404;
}
EOF
  echo "wrote HTTPS conf (cert detected at ${LIVE_DIR})"
else
  cat > "$AVAIL" <<EOF
server {
    listen 80;
${SITE_BODY}
}
EOF
  echo "wrote HTTP-only conf (no cert yet — run 02-deploy-ssl.sh after)"
fi

ln -sf "$AVAIL" "$ENABLED"

nginx -t
systemctl reload nginx

echo
if [[ -d "$LIVE_DIR" ]]; then
  echo "OK — https://${SITE}/"
  echo "Verify: bash script/03-verify.sh"
else
  echo "OK — http://${SITE}/"
  echo "Next:   sudo bash script/02-deploy-ssl.sh"
fi
