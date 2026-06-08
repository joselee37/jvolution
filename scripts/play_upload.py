#!/usr/bin/env python3
"""서명된 AAB를 Google Play 트랙에 업로드한다(Play Developer API).

AGP/Gradle 플러그인에 의존하지 않는다 — 빌드된 AAB + 서비스계정 JSON만 받는다.
edits.insert → bundles.upload → tracks.update → commit 트랜잭션.
"""
import argparse
import sys

from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.errors import HttpError
from googleapiclient.http import MediaFileUpload

SCOPES = ["https://www.googleapis.com/auth/androidpublisher"]


def main() -> None:
    p = argparse.ArgumentParser(description="Upload an AAB to a Google Play track.")
    p.add_argument("--aab", required=True, help="path to the signed .aab")
    p.add_argument("--package", required=True, help="applicationId, e.g. today.superb.jvl")
    p.add_argument("--json-key", required=True, help="service account JSON path")
    p.add_argument("--track", default="internal", help="Play track (default: internal)")
    p.add_argument("--release-name", default=None, help="release name shown in console")
    args = p.parse_args()

    creds = service_account.Credentials.from_service_account_file(args.json_key, scopes=SCOPES)
    service = build("androidpublisher", "v3", credentials=creds, cache_discovery=False)
    edits = service.edits()

    try:
        edit_id = edits.insert(body={}, packageName=args.package).execute()["id"]
        print(f"▸ edit {edit_id} opened")

        media = MediaFileUpload(args.aab, mimetype="application/octet-stream", resumable=True)
        version_code = edits.bundles().upload(
            packageName=args.package, editId=edit_id, media_body=media,
        ).execute()["versionCode"]
        print(f"▸ uploaded AAB → versionCode {version_code}")

        release = {"versionCodes": [str(version_code)], "status": "completed"}
        if args.release_name:
            release["name"] = args.release_name
        edits.tracks().update(
            packageName=args.package, editId=edit_id, track=args.track,
            body={"track": args.track, "releases": [release]},
        ).execute()
        print(f"▸ track '{args.track}' → versionCode {version_code}")

        edits.commit(packageName=args.package, editId=edit_id).execute()
        print(f"✓ committed — '{args.track}' track updated")
    except HttpError as e:
        print(f"Play API error: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
